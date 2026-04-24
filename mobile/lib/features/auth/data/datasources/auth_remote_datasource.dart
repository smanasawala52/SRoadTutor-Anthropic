import 'package:dio/dio.dart';

import '../../../../core/network/api_endpoints.dart';
import '../../domain/entities/user_role.dart';
import '../models/auth_response_model.dart';

/// Thin wrapper over Dio that understands the backend's `ApiResponse<T>`
/// envelope: `{ "data": { ... }, "timestamp": "..." }`.
///
/// Errors are *not* caught here on purpose — the repository does the
/// exception-to-Failure mapping in one place.
class AuthRemoteDataSource {
  AuthRemoteDataSource(this._dio);
  final Dio _dio;

  Future<AuthResponseModel> signup({
    required String email,
    required String password,
    required String fullName,
    required UserRole role,
    String? phoneNumber,
  }) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      ApiEndpoints.signup,
      data: {
        'email': email,
        'password': password,
        'fullName': fullName,
        'role': role.wireName,
        'phoneNumber': phoneNumber,
      },
    );
    return _unwrap(resp);
  }

  Future<AuthResponseModel> login({
    required String email,
    required String password,
  }) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      ApiEndpoints.login,
      data: {'email': email, 'password': password},
    );
    return _unwrap(resp);
  }

  Future<AuthResponseModel> google({
    required String idToken,
    UserRole? role,
  }) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      ApiEndpoints.google,
      data: {
        'token': idToken,
        if (role != null) 'role': role.wireName,
      },
    );
    return _unwrap(resp);
  }

  Future<AuthResponseModel> facebook({
    required String accessToken,
    UserRole? role,
  }) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      ApiEndpoints.facebook,
      data: {
        'token': accessToken,
        if (role != null) 'role': role.wireName,
      },
    );
    return _unwrap(resp);
  }

  Future<AuthResponseModel> refresh(String refreshToken) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      ApiEndpoints.refresh,
      data: {'refreshToken': refreshToken},
    );
    return _unwrap(resp);
  }

  Future<void> logout(String refreshToken) async {
    await _dio.post<Map<String, dynamic>>(
      ApiEndpoints.logout,
      data: {'refreshToken': refreshToken},
    );
  }

  AuthResponseModel _unwrap(Response<Map<String, dynamic>> resp) {
    // The Dio BaseOptions.validateStatus accepts <500, so we have to check
    // the status here to turn non-2xx responses into thrown DioExceptions
    // (which flow into the repository's mapException call).
    final status = resp.statusCode ?? 0;
    if (status >= 400) {
      throw DioException(
        requestOptions: resp.requestOptions,
        response: resp,
        type: DioExceptionType.badResponse,
      );
    }
    final data = resp.data?['data'];
    if (data is! Map<String, dynamic>) {
      throw FormatException('Malformed auth response: ${resp.data}');
    }
    return AuthResponseModel.fromJson(data);
  }
}
