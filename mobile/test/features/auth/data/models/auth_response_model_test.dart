import 'package:flutter_test/flutter_test.dart';
import 'package:sroadtutor/features/auth/data/models/auth_response_model.dart';
import 'package:sroadtutor/features/auth/domain/entities/user_role.dart';

void main() {
  group('AuthResponseModel.fromJson', () {
    test('parses a full payload into a domain session', () {
      final json = {
        'accessToken': 'acc123',
        'refreshToken': 'ref123',
        'user': {
          'id': 'u1',
          'email': 'a@b.com',
          'fullName': 'Alice',
          'role': 'INSTRUCTOR',
          'schoolId': 'sch1',
          'avatarUrl': null,
        },
      };

      final model = AuthResponseModel.fromJson(json);
      expect(model.accessToken, 'acc123');
      expect(model.refreshToken, 'ref123');

      final session = model.toSession();
      expect(session.user.email, 'a@b.com');
      expect(session.user.role, UserRole.instructor);
      expect(session.user.schoolId, 'sch1');
      expect(session.tokens.refreshToken, 'ref123');
    });

    test('handles optional schoolId/avatarUrl being absent', () {
      final json = {
        'accessToken': 'a',
        'refreshToken': 'r',
        'user': {
          'id': 'u',
          'email': 'x@y.com',
          'fullName': 'X',
          'role': 'STUDENT',
        },
      };
      final session = AuthResponseModel.fromJson(json).toSession();
      expect(session.user.hasSchool, isFalse);
    });
  });
}
