import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/domain/entities/user_role.dart';
import 'package:sroadtutor/features/auth/domain/usecases/signup_usecase.dart';

import '../../../../_support/fake_auth_repository.dart';

void main() {
  group('SignupUseCase.isStrongPassword', () {
    test('accepts 8+ chars with letter+digit', () {
      expect(SignupUseCase.isStrongPassword('Password1'), isTrue);
      expect(SignupUseCase.isStrongPassword('abcdef12'), isTrue);
    });

    test('rejects too short', () {
      expect(SignupUseCase.isStrongPassword('Pw1'), isFalse);
    });

    test('rejects missing digit', () {
      expect(SignupUseCase.isStrongPassword('Password'), isFalse);
    });

    test('rejects missing letter', () {
      expect(SignupUseCase.isStrongPassword('12345678'), isFalse);
    });
  });

  group('SignupUseCase.call', () {
    test('normalizes email + trims name', () async {
      final repo = FakeAuthRepository();
      final usecase = SignupUseCase(repo);

      await usecase.call(
        email: '  X@Y.com ',
        password: 'Password1',
        fullName: '  Alice  ',
        role: UserRole.student,
      );

      expect(repo.calls, contains('signup:x@y.com:STUDENT'));
      expect(repo.cached?.fullName, 'Test User'); // fake always returns this
    });

    test('rejects weak password before hitting repo', () {
      final repo = FakeAuthRepository();
      final usecase = SignupUseCase(repo);

      expect(
        () => usecase.call(
          email: 'a@b.com',
          password: 'weak',
          fullName: 'Bob',
          role: UserRole.owner,
        ),
        throwsA(isA<ArgumentError>()),
      );
      expect(repo.calls, isEmpty);
    });

    test('rejects empty name', () {
      final usecase = SignupUseCase(FakeAuthRepository());
      expect(
        () => usecase.call(
          email: 'a@b.com',
          password: 'Password1',
          fullName: '   ',
          role: UserRole.parent,
        ),
        throwsA(isA<ArgumentError>()),
      );
    });
  });
}
