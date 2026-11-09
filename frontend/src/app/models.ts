export type Stage = 'reported' | 'organized' | 'cleaned';
export interface Media { id: string; type: string; name: string; size: number; }
export interface CleanupResult { id:string; description:string; createdAt:number; before:Media[]; after:Media[]; }
export interface Place {
  results: CleanupResult[];
  id: string; title: string; location: string; city: string; country: string; description: string;
  stage: Stage; organizerName: string | null; date: string | null; time: string | null;
  timeZone: string | null; cleanupAt: number | null; target: number | null; supplies: string | null;
  result: string | null; live: { url: string; active: boolean } | null; createdAt: number;
  before: Media[]; after: Media[]; likes: number; liked: boolean; participants: number;
  joined: boolean; mine: boolean; organizerMine: boolean;
}
export interface EditRequest { title: string; location: string; city: string; country: string; description: string; }
export interface CleanupRequest { organizerName: string; date: string; time: string; timeZone: string; target: number; supplies: string; }
