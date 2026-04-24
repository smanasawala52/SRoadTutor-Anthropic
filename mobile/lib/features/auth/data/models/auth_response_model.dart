import '../../domain/entities/auth_session.dart';
import '../../domain/entities/auth_tokens.dart';
import 'user_model.dart';

/// Matches the backend `AuthResponse` record:
/// ```
/// { "accessToken": "...", "refreshToken": "...", "user": { ... } }
/// ```
class AuthResponseModel {
  const AuthResponseModel({
    required this.accessToken,
    required this.refreshToken,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;
  final UserModel user;

  factory AuthResponseModel.fromJson(Map<String, dynamic> json) =>
      AuthResponseModel(
        accessToken: json['accessToken'] as String,
        refreshToken: json['refreshToken'] as String,
        user: UserModel.fromJson(json['user'] as Map<String, dynamic>),
      );

  AuthSession toSession() => AuthSession(
        user: user.toEntity(),
        tokens: AuthTokens(
          accessToken: accessToken,
          refreshToken: refreshToken,
        ),
      );
}
