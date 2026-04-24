/// Mirrors the backend `com.sroadtutor.auth.model.Role` enum.
///
/// Keeping this as a plain enum (vs pulling the backend DTO directly) means
/// we can render user-facing strings in different languages without touching
/// network DTOs.
enum UserRole {
  owner('OWNER', 'School owner'),
  instructor('INSTRUCTOR', 'Instructor'),
  student('STUDENT', 'Student'),
  parent('PARENT', 'Parent');

  const UserRole(this.wireName, this.displayName);

  /// Value exchanged with the Spring Boot backend. Must match the server
  /// enum exactly.
  final String wireName;

  /// Human-readable label rendered in the UI.
  final String displayName;

  static UserRole fromWire(String value) {
    return UserRole.values.firstWhere(
      (r) => r.wireName == value,
      orElse: () => throw ArgumentError('Unknown role: $value'),
    );
  }
}
