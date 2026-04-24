import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart';
import 'package:logger/logger.dart';

import '../storage/local_database.dart';
import 'connectivity_service.dart';

/// Drains queued API calls from [LocalDatabase.pendingOps] whenever the
/// device is online.
///
/// Flow:
///   1. UI (or repository) enqueues an op via [enqueue].
///   2. If we're online right now, [_drain] is scheduled immediately.
///   3. Otherwise [ConnectivityService] will fire `true` when we come back
///      online and [_drain] runs then.
///   4. Each op is replayed as an HTTP call. On success → removed from DB.
///      On failure → retry count is incremented and `nextAttemptAt` is
///      pushed out using exponential backoff (max 1 hour).
class SyncQueue {
  SyncQueue({
    required LocalDatabase db,
    required Dio dio,
    required ConnectivityService connectivity,
    Logger? logger,
  })  : _db = db,
        _dio = dio,
        _connectivity = connectivity,
        _logger = logger ?? Logger();

  final LocalDatabase _db;
  final Dio _dio;
  final ConnectivityService _connectivity;
  final Logger _logger;

  StreamSubscription<bool>? _sub;
  bool _draining = false;

  /// Max number of retries before an op is dropped on the floor (and logged
  /// loudly — a prod app would alert on this).
  static const int _maxRetries = 20;

  void start() {
    _sub ??= _connectivity.onStatusChange.listen((online) {
      if (online) {
        unawaited(drainNow());
      }
    });
    if (_connectivity.isOnline) {
      unawaited(drainNow());
    }
  }

  Future<void> dispose() async {
    await _sub?.cancel();
    _sub = null;
  }

  Future<int> enqueue({
    required String opType,
    required String method,
    required String path,
    required Map<String, dynamic> payload,
  }) async {
    final now = DateTime.now().toUtc();
    final id = await _db.enqueuePendingOp(
      PendingOpsCompanion.insert(
        opType: opType,
        method: method,
        path: path,
        payloadJson: jsonEncode(payload),
        createdAt: now,
        nextAttemptAt: now,
      ),
    );
    if (_connectivity.isOnline) {
      unawaited(drainNow());
    }
    return id;
  }

  Future<void> drainNow() async {
    if (_draining) return;
    _draining = true;
    try {
      final now = DateTime.now().toUtc();
      final ops = await _db.pendingOpsReadyNow(now);
      for (final op in ops) {
        final succeeded = await _replay(op);
        if (succeeded) {
          await _db.deletePendingOp(op.id);
        } else {
          await _bumpRetry(op);
        }
      }
    } finally {
      _draining = false;
    }
  }

  Future<bool> _replay(PendingOp op) async {
    try {
      final body = jsonDecode(op.payloadJson);
      await _dio.request<dynamic>(
        op.path,
        data: body,
        options: Options(method: op.method),
      );
      return true;
    } on DioException catch (e) {
      // 4xx (except 401/429) means the server actively rejected the payload
      // — no point retrying blindly forever.
      final status = e.response?.statusCode ?? 0;
      if (status >= 400 && status < 500 && status != 401 && status != 429) {
        _logger.w(
          'Dropping pending op id=${op.id} op=${op.opType}: '
          'server rejected with $status, body=${e.response?.data}',
        );
        await _db.deletePendingOp(op.id);
        return true; // tell drainer it's "done", even though we gave up.
      }
      return false;
    } catch (e, st) {
      _logger.w('Replay of op ${op.id} failed', error: e, stackTrace: st);
      return false;
    }
  }

  Future<void> _bumpRetry(PendingOp op) async {
    final nextRetry = op.retryCount + 1;
    if (nextRetry > _maxRetries) {
      _logger.e(
        'Giving up on pending op id=${op.id} op=${op.opType} after '
        '$_maxRetries retries. Last error: ${op.lastError ?? "unknown"}',
      );
      await _db.deletePendingOp(op.id);
      return;
    }
    // Exponential backoff: 2^retry seconds, capped at 3600s (1 hour).
    final delaySeconds =
        (1 << nextRetry.clamp(0, 11)).clamp(1, 3600); // 2,4,8…3600
    final nextAt =
        DateTime.now().toUtc().add(Duration(seconds: delaySeconds));
    await (_db.update(_db.pendingOps)..where((p) => p.id.equals(op.id)))
        .write(PendingOpsCompanion(
      retryCount: Value(nextRetry),
      nextAttemptAt: Value(nextAt),
      lastError: const Value('replay failed'),
    ));
  }
}
