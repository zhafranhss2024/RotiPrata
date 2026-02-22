import type { AppRole, Profile } from "@/types";
import { mockProfile } from "@/mocks/profile";

/**
 * DUMMY DATA: Used when VITE_USE_MOCKS=true or when API calls fail in auto mode.
 */
export const mockAuthUser: Profile = mockProfile;
export const mockRoles: AppRole[] = ["user"];
