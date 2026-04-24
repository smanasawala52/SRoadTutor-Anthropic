import 'dart:async';

import 'package:dio/dio.dart';
import 'package:logger/logger.dart';
import 'package:pretty_dio_logger/pretty_dio_logger.dart';

import '../storage/secure_storage_service.dart';
import 'api_endpoints.dart';

/// Factory + interceptors for the shared [Dio] HTTP client.
///
/// Responsibilities it bakes in so no feature has to re-solve them:
///   1. `Authorization: Bearer <access_token>` header on every request.
///   2. On 401, hit `/auth/refresh` with the stored refresh token. If that
///      succeeds, replay the original request with the new access token.
///      If it fails, wipe local tokens and bubble the 401 so the UI can
///      route back to login.
///   3. Pretty-prints requests in dev (`enableDebugLogs=true`), silent in prod.
///
/// The class is intentionally not a Riverpod provider directly — it's built
/// by `dioClientProvider` in `auth_providers.dart` with the overridden deps.
class DioClientBuilder {
  DioClientBuilder({
    required this.baseUrl,
    required this.secureStorage,
    required this.logger,
    this.enableDebugLogs = false,
    this.onSessionExpired,
  });

  final String baseUrl;
  final SecureStorageService secureStorage;
  final Logger logger;
  final bool enableDebugLogs;

  /// Called when refresh fails and the user should be bounced to login.
  final FutureOr<void> Function()? onSessionExpired;

  Dio build() {
    final dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 15),
        sendTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 30),
        contentType: 'application/json',
        responseType: ResponseType.json,
        validateStatus: (status) => status != null && status < 500,
      ),
    );

    dio.interceptors.add(_AuthInterceptor(secureStorage));
    dio.interceptors.add(
      _RefreshInterceptor(
        dio: dio,
        baseUrl: baseUrl,
        secureStorage: secureStorage,
        logger: logger,
        onSessionExpired: onSessionExpired,
      ),
    );

    if (enableDebugLogs) {
      dio.interceptors.add(
        PrettyDioLogger(
          requestHeader: true,
          requestBody: true,
          responseBody: true,
          responseHeader: false,
          error: true,
          compact: true,
          maxWidth: 120,
        ),
      );
    }

    return dio;
  }
}

/// Attaches `Authorization: Bearer <token>` to every outbound request
/// except the `/auth/*` endpoints (login/signup/refresh don't need a token
/// and it would get in the way).
class _AuthInterceptor extends Interceptor {
  _AuthInterceptor(this._storage);
  final SecureStorageService _storage;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (_isPublicPath(options.path)) {
      return handler.next(options);
    }
    final token = await _storage.readAccessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  bool _isPublicPath(String path) {
    return path.startsWith('/auth/') || path == ApiEndpoints.health;
  }
}

/// Catches 401s, attempts a token refresh exactly once, and replays the
/// original request if refresh succeeds. Uses a single in-flight Future so
/// 10 concurrent 401s don't trigger 10 refresh calls.
class _RefreshInterceptor extends Interceptor {
  _RefreshInterceptor({
    required this.dio,
    required this.baseUrl,
    required this.secureStorage,
    required this.logger,
    this.onSessionExpired,
  });

  final Dio dio;
  final String baseUrl;
  final SecureStorageService secureStorage;
  final Logger logger;
  final FutureOr<void> Function()? onSessionExpired;

  Future<String?>? _inFlightRefresh;

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final status = err.response?.statusCode;
    final requestPath = err.requestOptions.path;
    final alreadyRetried =
        err.requestOptions.extra['__retried_after_refresh__'] == true;

    if (status != 401 ||
        requestPath.startsWith('/auth/') ||
        alreadyRetried) {
      return handler.next(err);
    }

    try {
      final newAccess = await (_inFlightRefresh ??= _refreshOnce());
      _inFlightRefresh = null;
      if (newAccess == null) {
        await _terminateSession();
        return handler.next(err);
      }
      // Replay the original request with the new token.
      final req = err.requestOptions;
      req.headers['Authorization'] = 'Bearer $newAccess';
      req.extra['__retried_after_refresh__'] = true;
      final response = await dio.fetch<dynamic>(req);
      handler.resolve(response);
    } catch (e, st) {
      _inFlightRefresh = null;
      logger.w('Token refresh failed', error: e, stackTrace: st);
      await _terminateSession();
      handler.next(err);
    }
  }

  Future<String?> _refreshOnce() async {
    final refresh = await secureStorage.readRefreshToken();
    if (refresh == null || refresh.isEmpty) return null;

    // Use a fresh Dio so we don't recurse through this same interceptor.
    final rawDio = Dio(BaseOptions(baseUrl: baseUrl, contentType: 'application/json'));
    final resp = await rawDio.post<Map<String, dynamic>>(
      ApiEndpoints.refresh,
      data: {'refreshToken': refresh},
    );
    final body = resp.data?['data'] as Map<String, dynamic>?;
    final access = body?['accessToken'] as String?;
    final newRefresh = body?['refreshToken'] as String?;
    if (access == null || newRefresh == null) return null;
    await secureStorage.writeAccessToken(access);
    await secureStorage.writeRefreshToken(newRefresh);
    return access;
  }

  Future<void> _terminateSession() async {
    await secureStorage.clear();
    if (onSessionExpired != null) {
      await onSessionExpired!.call();
    }
  }
}
