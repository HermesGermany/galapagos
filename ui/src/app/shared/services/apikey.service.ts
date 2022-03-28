import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, take } from 'rxjs/operators';
import { jsonHeader, ReplayContainer } from './services-common';
import { firstValueFrom } from 'rxjs';

export interface ApplicationApiKeyAndSecret {
    environmentId: string;

    apiKey: string;

    apiSecret: string;

}

export interface ApplicationApiKey {
    apiKey: string;

    issuedAt: string;

    userId: string;
}

export interface AuthenticationDetail {
    authenticationType: string;

    authentication: { [key: string]: string } | ApplicationApiKey;
}

export interface ApplicationApikeyAuthData {

    authentications: { [key: string]: AuthenticationDetail };

}

@Injectable()
export class ApiKeyService {

    private appApiKeys: { [appId: string]: ReplayContainer<ApplicationApikeyAuthData> } = {};

    constructor(private http: HttpClient) {
    }

    public getApplicationApiKeys(applicationId: string): ReplayContainer<ApplicationApikeyAuthData> {
        if (this.appApiKeys[applicationId]) {
            return this.appApiKeys[applicationId];
        }

        return this.appApiKeys[applicationId] = new ReplayContainer<ApplicationApikeyAuthData>(() =>
            this.http.get('/api/authentications/' + applicationId)
                .pipe(map(val => val as ApplicationApikeyAuthData)));
    }

    public getApplicationApiKeysPromise(applicationId: string): Promise<ApplicationApikeyAuthData> {
        return firstValueFrom(this.getApplicationApiKeys(applicationId).getObservable().pipe(take(1)));
    }

    public async requestApiKey(applicationId: string, environmentId: string): Promise<ApplicationApiKeyAndSecret> {
        return firstValueFrom(this.http.post<ApplicationApiKeyAndSecret>('/api/apikeys/' + applicationId + '/' + environmentId,
            {}, { headers: jsonHeader() }));
    }

}
