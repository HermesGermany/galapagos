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

@Injectable()
export class CertificateService {

    private envsWithDevCertSupport = new ReplayContainer<string[]>(() => this.http.get('/api/util/supported-devcert-environments'));

    constructor(private http: HttpClient) { }

    public getApplicationCertificates(applicationId: string): Promise<ApplicationCertificate[]> {
        return this.http.get('/api/certificates/' + applicationId).pipe(
            map(val => val as ApplicationCertificate[])).pipe(take(1)).toPromise();
    }

    public async requestAndDownloadApplicationCertificate(applicationId: string, environmentId: string, csrData: string,
        topicPrefix: string, extendCertificate: boolean): Promise<any> {
        let body = '';
        if (csrData) {
            body = JSON.stringify({
                csrData: csrData,
                topicPrefix: topicPrefix,
                extendCertificate: extendCertificate
            });
        } else {
            body = JSON.stringify({
                generateKey: true,
                topicPrefix: topicPrefix
            });
        }
        return this.http.post('/api/certificates/' + applicationId + '/' + environmentId, body, { headers: jsonHeader() }).toPromise()
            .then(resp => {
                const ra = resp as any;
                saveAs(this.base64ToBlob(ra.fileContentsBase64), ra.fileName);
            });
    }

    public async downloadDeveloperCertificate(environmentId: string): Promise<any> {
        return this.http.post('/api/me/certificates/' + environmentId, '').toPromise().then(resp => {
            const ra = resp as any;
            saveAs(this.base64ToBlob(ra.fileContentsBase64), ra.fileName);
        });
    }

    public getDeveloperCertificateInfo(environmentId: string): Observable<DeveloperCertificateInfo> {
        return this.http.get('/api/me/certificates/' + environmentId).pipe(map(data => data as DeveloperCertificateInfo));
    }

    public getEnvironmentsWithDevCertSupport(): Observable<string[]> {
        return this.envsWithDevCertSupport.getObservable();
    }

    private base64ToBlob(b64Data: string, sliceSize: number = 512) {
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
    }


}
