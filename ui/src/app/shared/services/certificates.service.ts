import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, take } from 'rxjs/operators';

import { saveAs } from 'file-saver';
import { jsonHeader, ReplayContainer } from './services-common';
import { Observable } from 'rxjs';

export interface ApplicationCertificate {
    environmentId: string;

    dn: string;

    certificateDownloadUrl: string;

    expiresAt: string;
}

export interface DeveloperCertificateInfo {
    dn: string;

    expiresAt: string;
}

export interface ApikeyInfo {
    apiKey: string;

    secret: string;

}

export interface AuthenticationResponse {
    [env: string]: EnvAuthenticationResponse;
}

interface EnvAuthenticationResponse {
    authenticationType: string;
    authentication: any;
}

const base64ToBlob = (b64Data: string, sliceSize: number = 512) => {
    const byteCharacters = atob(b64Data);
    const byteArrays = [];

    for (let offset = 0; offset < byteCharacters.length; offset += sliceSize) {
        const slice = byteCharacters.slice(offset, offset + sliceSize);

        const byteNumbers = new Array(slice.length);
        for (let i = 0; i < slice.length; i++) {
            byteNumbers[i] = slice.charCodeAt(i);
        }

        const byteArray = new Uint8Array(byteNumbers);
        byteArrays.push(byteArray);
    }

    return new Blob(byteArrays, { type: 'application/octet-stream' });
};

@Injectable()
export class CertificateService {

    private envsWithDevCertSupport = new ReplayContainer<string[]>(() => this.http.get('/api/util/supported-devcert-environments'));

    private envsWithDevApiKeySupport = new ReplayContainer<string[]>(() => this.http.get('/api/util/supported-apikey-environments'));

    private appCertificates: { [appId: string]: ReplayContainer<ApplicationCertificate[]> } = {};

    constructor(private http: HttpClient) {
    }

    public getApplicationCertificates(applicationId: string): ReplayContainer<ApplicationCertificate[]> {
        if (this.appCertificates[applicationId]) {
            return this.appCertificates[applicationId];
        }

        return this.appCertificates[applicationId] = new ReplayContainer<ApplicationCertificate[]>(() =>
            this.http.get('/api/authentications/' + applicationId)
                .pipe(map(val => this.toApplicationCertificates(val['authentications'] as AuthenticationResponse)
                )));
    }

    public getApplicationCn(applicationId: string): Promise<string> {
        return this.http.get('/api/util/common-name/' + applicationId).pipe(map(val => (val as any).cn)).toPromise();
    }

    public getApplicationCertificatesPromise(applicationId: string): Promise<ApplicationCertificate[]> {
        return this.getApplicationCertificates(applicationId).getObservable().pipe(take(1)).toPromise();
    }

    public async requestAndDownloadApplicationCertificate(applicationId: string, environmentId: string, csrData: string,
        extendCertificate: boolean): Promise<any> {
        let body;
        if (csrData) {
            body = JSON.stringify({
                csrData: csrData,
                extendCertificate: extendCertificate
            });
        } else {
            body = JSON.stringify({
                generateKey: true
            });
        }
        return this.http.post('/api/certificates/' + applicationId + '/' + environmentId, body, { headers: jsonHeader() }).toPromise()
            .then(resp => {
                const ra = resp as any;
                saveAs(base64ToBlob(ra.fileContentsBase64), ra.fileName);
            });
    }

    public async downloadDeveloperCertificate(environmentId: string): Promise<any> {
        return this.http.post('/api/me/certificates/' + environmentId, '').toPromise().then(resp => {
            const ra = resp as any;
            saveAs(base64ToBlob(ra.fileContentsBase64), ra.fileName);
        });
    }

    public async downloadDeveloperApiKey(environmentId: string): Promise<any> {
        return this.http.post('/api/me/apikey/' + environmentId, '').toPromise().then(resp => {
            const ra = resp as ApikeyInfo;
            console.log(ra);
            const content = `Key: ${ra.apiKey}, Secret: ${ra.secret}`;
            saveAs(new Blob([content], { type: 'application/octet-stream' }));
        });
    }

    public getDeveloperAuthenticationInfo(environmentId: string): Observable<AuthenticationResponse> {
        return this.http.get('/api/me/authentications/' + environmentId).pipe(map(data => data as AuthenticationResponse));
    }

    public getEnvironmentsWithDevCertSupport(): Observable<string[]> {
        return this.envsWithDevCertSupport.getObservable();
    }

    public getEnvironmentsWithDevApikeySupport(): Observable<string[]> {
        return this.envsWithDevApiKeySupport.getObservable();
    }

    private toApplicationCertificates(response: AuthenticationResponse): ApplicationCertificate[] {
        return Object.keys(response).map(key => this.toEnvAuthenticationCertificate(key, response[key])).filter(v => v !== null);
    }

    private toEnvAuthenticationCertificate(envId: string, envResponse: EnvAuthenticationResponse): ApplicationCertificate {
        if (envResponse.authenticationType !== 'certificates') {
            return null;
        }

        return {
            environmentId: envId,
            dn: envResponse.authentication['dn'],
            expiresAt: envResponse.authentication['expiresAt'],
            certificateDownloadUrl: null
        };
    }

}
