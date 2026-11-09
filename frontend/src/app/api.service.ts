import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CleanupRequest, EditRequest, Place } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  list() { return firstValueFrom(this.http.get<{ places: Place[] }>('/api/places')); }
  create(data: FormData) { return firstValueFrom(this.http.post<{ id: string }>('/api/places', data)); }
  edit(id: string, data: EditRequest) { return this.post(id, 'edit', data); }
  organize(id: string, data: CleanupRequest) { return this.post(id, 'cleanup', data); }
  join(id: string, active: boolean) { return this.post(id, 'join', { active }); }
  like(id: string, active: boolean) { return this.post(id, 'like', { active }); }
  result(id: string, data: FormData) { return this.post(id, 'after', data); }
  live(id: string, active: boolean, url?: string, consent?: boolean) { return this.post(id, 'live', { active, url, consent }); }
  private post(id: string, action: string, body: unknown) {
    return firstValueFrom(this.http.post<{ ok: boolean }>(`/api/places/${encodeURIComponent(id)}/${action}`, body));
  }
  error(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'Cannot reach the server. Check your connection and make sure the backend is running.';
      return typeof error.error?.error === 'string' ? error.error.error : 'The request could not be completed. Please try again.';
    }
    return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
  }
}
