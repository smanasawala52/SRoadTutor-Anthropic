import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/core/storage/secure_storage_service.dart';

void main() {
  group('InMemorySecureStorageService', () {
    late InMemorySecureStorageService s;

    setUp(() => s = InMemorySecureStorageService());

    test('round-trips access token', () async {
      await s.writeAccessToken('acc');
      expect(await s.readAccessToken(), 'acc');
    });

    test('round-trips refresh token + user id', () async {
      await s.writeRefreshToken('ref');
      await s.writeUserId('u1');
      expect(await s.readRefreshToken(), 'ref');
      expect(await s.readUserId(), 'u1');
    });

    test('clear wipes everything', () async {
      await s.writeAccessToken('a');
      await s.writeRefreshToken('r');
      await s.writeUserId('u');
      await s.clear();
      expect(await s.readAccessToken(), isNull);
      expect(await s.readRefreshToken(), isNull);
      expect(await s.readUserId(), isNull);
    });
  });
}
