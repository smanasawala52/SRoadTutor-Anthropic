import 'package:equatable/equatable.dart';

import 'user_role.dart';

/// Clean-architecture *domain* entity for a signed-in user.
///
/// This is the type the UI consumes. It's deliberately decoupled from the
/// network shape ([UserModel] in data/) so a future API change doesn't ripple
/// through every screen.
class User extends Equatable {
  const User({
    required this.id,
    required this.email,
    required this.fullName,
    required this.role,
    this.schoolId,
    this.avatarUrl,
  });

  final String id;
  final String email;
  final String fullName;
  final UserRole role;
  final String? schoolId;
  final String? avatarUrl;

  bool get hasSchool => schoolId != null && schoolId!.isNotEmpty;

  @override
  List<Object?> get props => [id, email, fullName, role, schoolId, avatarUrl];
}
