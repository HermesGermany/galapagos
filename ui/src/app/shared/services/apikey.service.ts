import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, take } from 'rxjs/operators';
import { jsonHeader, ReplayContainer } from './services-common';

export interface ApplicationApikey {
    environmentId: string;

    apiKey: string;

    apiSecret: string;

}

@Injectable()
export class ApiKeyService {

    private appApiKeys: { [appId: string]: ReplayContainer<ApplicationApikey[]> } = {};

    constructor(private http: HttpClient) {
    }

    public getApplicationApiKeys(applicationId: string, environmentId: string): ReplayContainer<ApplicationApikey[]> {
        if (this.appApiKeys[applicationId]) {
            return this.appApiKeys[applicationId];
        }

        return this.appApiKeys[applicationId] = new ReplayContainer<ApplicationApikey[]>(() =>
            this.http.get('/api/apikeys/' + applicationId + '/' + environmentId)
                .pipe(map(val => val as ApplicationApikey[])));
    }

    public getApplicationApiKeysPromise(applicationId: string, environmentId: string): Promise<ApplicationApikey[]> {
        return this.getApplicationApiKeys(applicationId, environmentId).getObservable().pipe(take(1)).toPromise();
    }

    public async requestApiKey(applicationId: string, environmentId: string): Promise<ApplicationApikey> {

        return this.http.post<ApplicationApikey>('/api/apikeys/' + applicationId + '/' + environmentId, {}, { headers: jsonHeader() })
            .toPromise();

    }

}
