import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Abstract contract for token storage, so tests can inject
/// [InMemorySecureStorageService] without hitting the real Keychain /
/// EncryptedSharedPreferences.
abstract class SecureStorageService {
  /// Factory for prod: returns the real, platform-backed implementation.
  const factory SecureStorageService() = _PlatformSecureStorageService;

  Future<void> writeAccessToken(String token);
  Future<String?> readAccessToken();

  Future<void> writeRefreshToken(String token);
  Future<String?> readRefreshToken();

  Future<void> writeUserId(String userId);
  Future<String?> readUserId();

  /// Wipes every key written by this app. Used during logout.
  Future<void> clear();
}

/// Real implementation backed by `flutter_secure_storage`.
class _PlatformSecureStorageService implements SecureStorageService {
  const _PlatformSecureStorageService();

  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  static const _kAccessToken = 'auth.access_token';
  static const _kRefreshToken = 'auth.refresh_token';
  static const _kUserId = 'auth.user_id';

  @override
  Future<void> writeAccessToken(String token) =>
      _storage.write(key: _kAccessToken, value: token);
  @override
  Future<String?> readAccessToken() => _storage.read(key: _kAccessToken);

  @override
  Future<void> writeRefreshToken(String token) =>
      _storage.write(key: _kRefreshToken, value: token);
  @override
  Future<String?> readRefreshToken() => _storage.read(key: _kRefreshToken);

  @override
  Future<void> writeUserId(String userId) =>
      _storage.write(key: _kUserId, value: userId);
  @override
  Future<String?> readUserId() => _storage.read(key: _kUserId);

  @override
  Future<void> clear() async {
    await Future.wait([
      _storage.delete(key: _kAccessToken),
      _storage.delete(key: _kRefreshToken),
      _storage.delete(key: _kUserId),
    ]);
  }
}

/// In-memory stand-in used by unit + widget tests.
class InMemorySecureStorageService implements SecureStorageService {
  final Map<String, String> _data = {};

  @override
  Future<String?> readAccessToken() async => _data['access'];
  @override
  Future<String?> readRefreshToken() async => _data['refresh'];
  @override
  Future<String?> readUserId() async => _data['userId'];

  @override
  Future<void> writeAccessToken(String token) async => _data['access'] = token;
  @override
  Future<void> writeRefreshToken(String token) async =>
      _data['refresh'] = token;
  @override
  Future<void> writeUserId(String userId) async => _data['userId'] = userId;

  @override
  Future<void> clear() async => _data.clear();
}
