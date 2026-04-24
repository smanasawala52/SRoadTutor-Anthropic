import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/domain/usecases/login_with_email_usecase.dart';

import '../../../../_support/fake_auth_repository.dart';

void main() {
  group('LoginWithEmailUseCase', () {
    test('lower-cases + trims the email before hitting the repo', () async {
      final repo = FakeAuthRepository();
      final usecase = LoginWithEmailUseCase(repo);

      await usecase.call(email: '  FOO@EXAMPLE.com ', password: 'Password1');

      expect(repo.calls, contains('loginWithEmail:foo@example.com'));
    });

    test('rejects empty email', () {
      final usecase = LoginWithEmailUseCase(FakeAuthRepository());
      expect(
        () => usecase.call(email: '   ', password: 'Password1'),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('rejects empty password', () {
      final usecase = LoginWithEmailUseCase(FakeAuthRepository());
      expect(
        () => usecase.call(email: 'a@b.com', password: ''),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('propagates repo errors untouched', () async {
      final repo = FakeAuthRepository(throwOnLogin: true);
      final usecase = LoginWithEmailUseCase(repo);
      expect(
        () => usecase.call(email: 'a@b.com', password: 'Password1'),
        throwsA(isA<StateError>()),
      );
    });
  });
}
