import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/core/errors/exception_mapper.dart';
import 'package:sroadtutor/core/errors/failure.dart';

void main() {
  group('mapException', () {
    test('SocketException → NetworkFailure', () {
      expect(mapException(const SocketException('boom')),
          isA<NetworkFailure>());
    });

    test('DioException.connectionTimeout → NetworkFailure', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/x'),
        type: DioExceptionType.connectionTimeout,
      );
      expect(mapException(e), isA<NetworkFailure>());
    });

    test('DioException 401 → UnauthorizedFailure', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/x'),
        response: Response(
          requestOptions: RequestOptions(path: '/x'),
          statusCode: 401,
          data: {'message': 'bye', 'code': 'UNAUTHORIZED'},
        ),
        type: DioExceptionType.badResponse,
      );
      expect(mapException(e), isA<UnauthorizedFailure>());
    });

    test('DioException 400 with body → ApiFailure', () {
      final e = DioException(
        requestOptions: RequestOptions(path: '/x'),
        response: Response(
          requestOptions: RequestOptions(path: '/x'),
          statusCode: 400,
          data: {'message': 'Validation', 'code': 'VALIDATION_ERROR'},
        ),
        type: DioExceptionType.badResponse,
      );
      final f = mapException(e);
      expect(f, isA<ApiFailure>());
      expect((f as ApiFailure).code, 'VALIDATION_ERROR');
      expect(f.statusCode, 400);
    });

    test('FormatException → UnknownFailure', () {
      expect(mapException(const FormatException('nope')), isA<UnknownFailure>());
    });
  });
}
