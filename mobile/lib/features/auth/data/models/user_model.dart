import '../../domain/entities/user.dart';
import '../../domain/entities/user_role.dart';

/// Network-shape mirror of the backend `UserDto`.
///
/// Keeping JSON concerns confined to this file (`fromJson`/`toJson`) means
/// the domain [User] never has to grow keys like `snake_case` nor know
/// about null-handling quirks.
class UserModel {
  const UserModel({
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
  final String role;
  final String? schoolId;
  final String? avatarUrl;

  factory UserModel.fromJson(Map<String, dynamic> json) => UserModel(
        id: json['id'] as String,
        email: json['email'] as String,
        fullName: json['fullName'] as String,
        role: json['role'] as String,
        schoolId: json['schoolId'] as String?,
        avatarUrl: json['avatarUrl'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'email': email,
        'fullName': fullName,
        'role': role,
        'schoolId': schoolId,
        'avatarUrl': avatarUrl,
      };

  User toEntity() => User(
        id: id,
        email: email,
        fullName: fullName,
        role: UserRole.fromWire(role),
        schoolId: schoolId,
        avatarUrl: avatarUrl,
      );

  static UserModel fromEntity(User u) => UserModel(
        id: u.id,
        email: u.email,
        fullName: u.fullName,
        role: u.role.wireName,
        schoolId: u.schoolId,
        avatarUrl: u.avatarUrl,
      );
}
