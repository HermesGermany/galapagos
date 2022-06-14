import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, take } from 'rxjs/operators';
import { jsonHeader, ReplayContainer } from './services-common';
import { ApikeyInfo } from './certificates.service';

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

export interface ServiceAccountId {
    accountId: string;
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
        return this.getApplicationApiKeys(applicationId).getObservable().pipe(take(1)).toPromise();
    }

    public getApplicationServiceAccountId(applicationId: string, environmentId: string): Promise<ServiceAccountId> {
        return this.http.get<ServiceAccountId>('/api/service-account/' + environmentId + '/' + applicationId).toPromise();
    }

    public async requestApiKey(applicationId: string, environmentId: string): Promise<ApplicationApiKeyAndSecret> {
        return this.http.post<ApplicationApiKeyAndSecret>('/api/apikeys/' + applicationId + '/' + environmentId,
            {}, { headers: jsonHeader() })
            .toPromise();
    }

    public async createDeveloperApiKey(environmentId: string): Promise<any> {
        return this.http.post('/api/me/apikey/' + environmentId, '').toPromise().then(resp => {
            const ra = resp as ApikeyInfo;
            return { key: ra.apiKey, secret: ra.secret };
        });
    }

}
