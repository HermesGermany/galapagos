import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, take } from 'rxjs/operators';
import { ReplayContainer } from './services-common';

export interface ApplicationApikey {
    environmentId: string;

    key: string;

    secret: string;

}

@Injectable()
export class ApiKeyService {

    private appApiKeys: { [appId: string]: ReplayContainer<ApplicationApikey[]> } = {};

    constructor(private http: HttpClient) {
    }

    public getApplicationApiKeys(applicationId: string): ReplayContainer<ApplicationApikey[]> {
        if (this.appApiKeys[applicationId]) {
            return this.appApiKeys[applicationId];
        }

        return this.appApiKeys[applicationId] = new ReplayContainer<ApplicationApikey[]>(() =>
            this.http.get('/api/certificates/' + applicationId).pipe(map(val => val as ApplicationApikey[])));
    }

    public getApplicationApiKeysPromise(applicationId: string): Promise<ApplicationApikey[]> {
        return this.getApplicationApiKeys(applicationId).getObservable().pipe(take(1)).toPromise();
    }

    public async requestApiKey(applicationId: string, environmentId: string): Promise<ApplicationApikey> {

        return Promise.resolve({

            environmentId: 'devtest',

            key: 'testkey',

            secret: 'testsecret'

        });

        const body = JSON.stringify({});

        // return this.http.post('/api/certificates/' + applicationId + '/' + environmentId, body, { headers: jsonHeader() }).toPromise();

    }

}
