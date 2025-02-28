import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { jsonHeader, ReplayContainer } from './services-common';

export type Role = 'ADMIN' | 'TESTER' | 'VIEWER';
export const AVAILABLE_ROLES: Role[] = ['ADMIN', 'TESTER', 'VIEWER']

export interface RoleDto {
    id: string;
    userName: string;
    role: Role;
    applicationId: string;
    environmentId: string;
    comments: string;
    state: 'SUBMITTED' | 'REJECTED' | 'ACCEPTED' | 'REVOKED';
    //state: string;
    createdAt: string;
    lastStatusChangeAt: string;
    lastStatusChangeBy: string;
}

export interface CreateUserRoleDto {
    userName: string;
    role: Role;
    applicationId: string;
    comments: string;
}

@Injectable()
export class RoleService {

    private userRoles = new ReplayContainer<RoleDto[]>(() => this.http.get<RoleDto[]>('/api/me/roles'))

    private allRoles = new ReplayContainer<RoleDto[]>(() => this.http.get<RoleDto[]>('/api/admin/roles'))

    constructor(private http: HttpClient) {
    }

    public listUserRoles(): Observable<RoleDto[]> {
        return this.userRoles.getObservable();
    }

    public listAllRoles(): Observable<RoleDto[]> {
        return this.allRoles.getObservable();
    }

    public getAllRoles(environmentId: string): Observable<RoleDto[]> {
        return this.http.get<RoleDto[]>(`/api/me/roles/${environmentId}`);
    }

    public getRolesForUser(environmentId: string, userName: string): Observable<RoleDto[]> {
        return this.http.get<RoleDto[]>(`/api/roles/${environmentId}/${userName}`);
    }

    public async addUserRole(environmentId: string, data: CreateUserRoleDto): Promise<void> {
        return firstValueFrom(this.http.put<void>(`/api/roles/${environmentId}`, data)).then(() => {
            this.refresh();
        });
    }

    public async deleteUserRoles(environmentId: string, userName: string): Promise<void> {
        return firstValueFrom(this.http.delete<void>(`/api/roles/${environmentId}/${userName}`)).then(() => {
            this.refresh();
        });
    }

    public async deleteUserRoleById(environmentId: string, id: string): Promise<void> {
        return firstValueFrom(this.http.delete<void>(`/api/roles/${environmentId}/prefixes/${id}`)).then(() => {
            this.refresh();
        });
    }

    public async updateRole(requestId: string, environmentId: string, newState: string): Promise<any> {
        const body = JSON.stringify({ newState: newState });
        return firstValueFrom(this.http.post('/api/admin/roles/requests/' + requestId + environmentId, body, { headers: jsonHeader() })).then(() => {
            this.refresh();
        });
    }

    public async refresh(): Promise<any> {
        return this.userRoles.refresh().then(this.allRoles.refresh);
    }
}
