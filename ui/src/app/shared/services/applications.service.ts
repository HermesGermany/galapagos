import { Injectable } from '@angular/core';
import { Observable, ReplaySubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { jsonHeader, ReplayContainer } from './services-common';

export interface ApplicationInfo {
    id: string;

    name: string;

    infoUrl?: string;

    aliases: string[];
}

export interface BusinessCapabilityInfo {

    id: string;

    name: string;

    topicNamePrefix: string;
}

export interface UserApplicationInfo extends ApplicationInfo {

    kafkaGroupPrefix: string;

    businessCapabilities: BusinessCapabilityInfo[];

}

export interface ApplicationOwnerRequest {
    id: string;

    applicationId: string;

    createdAt: string;

    userName: string;

    comments: string;

    state: 'SUBMITTED' | 'REJECTED' | 'ACCEPTED' | 'REVOKED';

    lastStatusChangeAt: string;

    lastStatusChangeBy: string;
}

export interface ApplicationTopicSubscription {

    topicName: string;

}

export interface ApplicationNameService {

    getAppName(applicationId: string): Observable<string>;

}

class ApplicationNameServiceImpl implements ApplicationNameService {

    private apps: Observable<ApplicationInfo[]>;

    private appNameCache: { [id: string]: Observable<string> } = { };

    constructor(apps: Observable<ApplicationInfo[]>) {
        this.apps = apps;
    }

    getAppName(applicationId: string): Observable<string> {
        if (this.appNameCache[applicationId]) {
            return this.appNameCache[applicationId];
        }

        const subj = new ReplaySubject<string>(1);

        this.appNameCache[applicationId] = subj;

        const extractName = (infos: ApplicationInfo[]) => {
            const app = infos.find(a => a.id === applicationId);
            return app ? app.name : applicationId;
        };

        this.apps.pipe(map(infos => extractName(infos))).subscribe(subj);
        return subj;
    }
}

@Injectable()
export class ApplicationsService {

    private availableAppsIncl = new ReplayContainer<ApplicationInfo[]>(() => this.http.get('/api/applications?excludeUserApps=false'));

    private availableAppsExcl = new ReplayContainer<ApplicationInfo[]>(() => this.http.get('/api/applications?excludeUserApps=true'));

    private userRequests = new ReplayContainer<ApplicationOwnerRequest[]>(() => this.http.get('/api/me/requests'));

    private userApplications = new ReplayContainer<UserApplicationInfo[]>(() => this.http.get('/api/me/applications'));

    private allRequests = new ReplayContainer<ApplicationOwnerRequest[]>(() => this.http.get('/api/admin/requests'));

    constructor(private http: HttpClient) { }

    public getAvailableApplications(excludeUserApps: boolean): Observable<ApplicationInfo[]> {
        return excludeUserApps ? this.availableAppsExcl.getObservable() : this.availableAppsIncl.getObservable();
    }

    public getUserApplications(): ReplayContainer<UserApplicationInfo[]> {
        return this.userApplications;
    }

    public getUserApplicationOwnerRequests(): Observable<ApplicationOwnerRequest[]> {
        return this.userRequests.getObservable();
    }

    public getAllApplicationOwnerRequests(): Observable<ApplicationOwnerRequest[]> {
        return this.allRequests.getObservable();
    }

    public getApplicationSubscriptions(appId: string, envId: string): Observable<ApplicationTopicSubscription[]> {
        return this.http.get<ApplicationTopicSubscription[]>('/api/applications/' + appId + '/subscriptions/' + envId);
    }

    public async submitApplicationOwnerRequest(applicationId: string, comments: string): Promise<ApplicationOwnerRequest> {
        const body = JSON.stringify({
            applicationId: applicationId,
            comments: comments || null
         });

        return this.http.put('/api/me/requests', body, { headers: jsonHeader() }).toPromise().then(value => {
            this.userRequests.refresh();
            this.availableAppsExcl.refresh();
            this.availableAppsIncl.refresh();
            return <ApplicationOwnerRequest>value;
        });
    }

    public async cancelApplicationOwnerRequest(requestId: string): Promise<any> {
        return this.http.delete('/api/me/requests/' + requestId).toPromise().then(() => {
            this.userRequests.refresh();
            this.allRequests.refresh();
            this.availableAppsExcl.refresh();
            this.availableAppsIncl.refresh();
        });
    }

    public async updateApplicationOwnerRequest(requestId: string, newState: string): Promise<any> {
        const body = JSON.stringify({ newState: newState });
        return this.http.post('/api/admin/requests/' + requestId, body, { headers: jsonHeader() }).toPromise().then(() => {
            this.userRequests.refresh();
            this.allRequests.refresh();
            this.userApplications.refresh();
        });
    }

    /*public async updateApplication(applicationId: string, kafkaGroupPrefix: string): Promise<any> {
        const body = JSON.stringify({ kafkaGroupPrefix: kafkaGroupPrefix });
        return this.http.post('/api/applications/' + applicationId, body, { headers: jsonHeader() }).toPromise().then(
            () => this.userApplications.refresh());
    }*/

    public async refresh(): Promise<any> {
        return Promise.all([
            this.userRequests.refresh(),
            this.allRequests.refresh()
        ]);
    }

    public newApplicationNameService(): ApplicationNameService {
        return new ApplicationNameServiceImpl(this.getAvailableApplications(false));
    }

}
