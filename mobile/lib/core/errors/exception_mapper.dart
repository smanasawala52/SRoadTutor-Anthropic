import 'dart:io';

import 'package:dio/dio.dart';

import 'failure.dart';

/// Converts raw infrastructure exceptions into domain [Failure]s.
///
/// Repositories call this in their catch blocks so screens never have to
/// know about `DioException` vs `SocketException`.
Failure mapException(Object error) {
  if (error is DioException) {
    return _mapDio(error);
  }
  if (error is SocketException || error is HttpException) {
    return const NetworkFailure();
  }
  if (error is FormatException) {
    return const UnknownFailure('Server returned malformed data');
  }
  return UnknownFailure(error.toString());
}

Failure _mapDio(DioException e) {
  switch (e.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.sendTimeout:
    case DioExceptionType.receiveTimeout:
    case DioExceptionType.connectionError:
      return const NetworkFailure('Network timed out — please try again');
    case DioExceptionType.cancel:
      return const UnknownFailure('Request was cancelled');
    case DioExceptionType.badCertificate:
      return const UnknownFailure('Invalid server certificate');
    case DioExceptionType.unknown:
      if (e.error is SocketException) {
        return const NetworkFailure();
      }
      return UnknownFailure(e.message ?? 'Unknown network error');
    case DioExceptionType.badResponse:
      return _mapBadResponse(e);
  }
}

Failure _mapBadResponse(DioException e) {
  final status = e.response?.statusCode ?? 0;
  final body = e.response?.data;

  String message = 'Request failed ($status)';
  String code = 'HTTP_$status';

  if (body is Map<String, dynamic>) {
    final msg = body['message'];
    final c = body['code'];
    if (msg is String && msg.isNotEmpty) message = msg;
    if (c is String && c.isNotEmpty) code = c;
  }

  if (status == 401) {
    return UnauthorizedFailure(message);
  }
  return ApiFailure(message: message, code: code, statusCode: status);
}
