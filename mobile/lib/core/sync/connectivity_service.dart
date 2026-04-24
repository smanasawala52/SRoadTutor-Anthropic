import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';

/// Stream-based wrapper over `connectivity_plus`.
///
/// Emits `true` when the device has *any* form of connectivity (WiFi, mobile,
/// ethernet), `false` otherwise. Consumers should treat this as a hint: even
/// a `true` value doesn't guarantee the backend is reachable — the sync
/// engine still retries with exponential backoff on actual failures.
class ConnectivityService {
  ConnectivityService({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity();

  final Connectivity _connectivity;
  final _controller = StreamController<bool>.broadcast();
  StreamSubscription<List<ConnectivityResult>>? _sub;
  bool _lastKnown = false;

  /// Snapshot of the most recent connectivity value.
  bool get isOnline => _lastKnown;

  /// Subscribe to online/offline transitions.
  Stream<bool> get onStatusChange => _controller.stream;

  Future<void> start() async {
    final initial = await _connectivity.checkConnectivity();
    _lastKnown = _isOnline(initial);
    _controller.add(_lastKnown);

    _sub = _connectivity.onConnectivityChanged.listen((results) {
      final online = _isOnline(results);
      if (online != _lastKnown) {
        _lastKnown = online;
        _controller.add(online);
      }
    });
  }

  Future<void> dispose() async {
    await _sub?.cancel();
    await _controller.close();
  }

  bool _isOnline(List<ConnectivityResult> results) =>
      results.any((r) => r != ConnectivityResult.none);
}
