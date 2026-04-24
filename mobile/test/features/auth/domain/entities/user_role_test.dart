import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/domain/entities/user_role.dart';

void main() {
  group('UserRole', () {
    test('wire ↔ enum round-trips', () {
      for (final r in UserRole.values) {
        expect(UserRole.fromWire(r.wireName), r);
      }
    });

    test('display names are non-empty', () {
      for (final r in UserRole.values) {
        expect(r.displayName, isNotEmpty);
      }
    });

    test('throws on unknown wire value', () {
      expect(() => UserRole.fromWire('ADMIN'), throwsA(isA<ArgumentError>()));
    });
  });
}
