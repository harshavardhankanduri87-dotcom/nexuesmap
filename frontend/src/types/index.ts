export interface UserSummary {
  id: number;
  name: string;
  email: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  ghostMode: boolean;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface BackendHealth {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  service: string;
  version: string;
  timestamp: string;
  environment: string;
  subsystems?: Record<string, string>;
}

export interface Coordinates {
  lat: number;
  lng: number;
  accuracy?: number;
}

export type GeolocationStatus =
  | 'getting_location'
  | 'location_available'
  | 'permission_denied'
  | 'location_unavailable';

export interface CampusZone {
  id: string;
  name: string;
  code: string;
  center: [number, number];
  polygon: [number, number][];
  color: string;
  activeUsers: number;
  description: string;
}

export interface UserLocationState {
  coords: Coordinates | null;
  zone: CampusZone | null;
  isGhostMode: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface ChatMessage {
  id: number;
  senderId: number | null;
  senderName: string;
  zoneId: number;
  content: string;
  isGhostMode: boolean;
  createdAt: string;
}

export interface PresenceEvent {
  zoneId: number;
  userEmail: string;
  userName: string;
  eventType: 'JOIN' | 'LEAVE' | 'HEARTBEAT';
  activeMemberCount: number;
  timestamp: string;
  isGhostMode: boolean;
}

export interface PaginatedMessagesResponse {
  messages: ChatMessage[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

export type PinCategory =
  | 'MEETUP'
  | 'EVENT'
  | 'LOST_AND_FOUND'
  | 'FOOD'
  | 'EMERGENCY'
  | 'CAMPUS_ISSUE'
  | 'GENERAL_ANNOUNCEMENT';

export type PinStatus = 'ACTIVE' | 'EXPIRED' | 'DELETED';

export interface MeetupResponse {
  id: number;
  pinId: number;
  startTime: string;
  endTime?: string;
  capacity?: number;
  currentParticipants: number;
  isJoined: boolean;
  isFull: boolean;
  active: boolean;
}

export interface PinResponse {
  id: number;
  title: string;
  description: string;
  category: PinCategory;
  status: PinStatus;
  latitude: number;
  longitude: number;
  distanceMeters?: number;
  creatorId?: number;
  creatorName: string;
  isCreator: boolean;
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
  meetup?: MeetupResponse;
}

export interface PaginatedPinsResponse {
  pins: PinResponse[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

export interface PinEvent {
  eventType: string;
  pinId: number;
  pin?: PinResponse;
  timestamp: string;
}

export interface PrivacySettings {
  ghostMode: boolean;
  locationSharingEnabled: boolean;
  fuzzingRadiusMeters: number;
  updatedAt: string;
}

export interface PeerLocation {
  userId: number;
  displayName: string;
  latitude: number;
  longitude: number;
  fuzzingRadiusMeters: number;
  zoneId: number;
  zoneName: string;
  lastSeenAt: string;
}

export interface AdminOverview {
  totalRegisteredUsers: number;
  currentlyActiveUsers: number;
  activeZonesCount: number;
  activePinsCount: number;
  activeMeetupsCount: number;
  totalMessagesCount: number;
  messagesInLast24Hours: number;
  timestamp: string;
  metricDefinitions: Record<string, string>;
}

export interface ZoneOccupancy {
  zoneId: number;
  zoneName: string;
  zoneCode: string;
  zoneType: string;
  activeMembers: number;
  privacyEligibleMembers: number;
  centerLat: number;
  centerLng: number;
  leafletCoordinates: [number, number][];
}

export interface ActivityBucket {
  label: string;
  bucketStart: string;
  bucketEnd: string;
  messageCount: number;
  locationUpdateCount: number;
  pinCreationCount: number;
}

export interface ActivityTrend {
  interval: string;
  rangeStart: string;
  rangeEnd: string;
  totalMessages: number;
  totalLocationUpdates: number;
  totalPinsCreated: number;
  buckets: ActivityBucket[];
}

export interface ZoneDensityPoint {
  zoneId: number;
  zoneName: string;
  zoneCode: string;
  centerLat: number;
  centerLng: number;
  boundaryCoordinates: [number, number][];
  userCount: number;
  intensity: number;
  densityLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
}

export interface DensityHeatmap {
  minThreshold: number;
  totalObservedMembers: number;
  privacyEligibleMembers: number;
  suppressedAreasCount: number;
  densityPoints: ZoneDensityPoint[];
}
