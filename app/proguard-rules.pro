# Release shrinking rules. Room, Compose and kotlinx.serialization ship their own; add rules here only for
# something R8 removes or renames that the app reaches by reflection.

# The Play review library refers to an annotation from Play services, which the app does not include and never needs.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
