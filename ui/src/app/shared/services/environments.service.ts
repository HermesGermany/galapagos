import { Injectable } from '@angular/core';
import { EMPTY, Observable, ReplaySubject } from 'rxjs';
import { jsonHeader, ReplayContainer } from './services-common';
import { HttpClient } from '@angular/common/http';
import { map, take } from 'rxjs/operators';
import { ToastService } from '../modules/toast/toast.service';
import { ApplicationInfo } from './applications.service';

const LOCAL_STORAGE_ENV_KEY = 'galapagos.environment';

export interface KafkaEnvironment {

    id: string;

    name: string;

    bootstrapServers: string;

    production: boolean;

    stagingOnly: boolean;

}

export interface EnvironmentServerInfo {

    server: string;

    online: boolean;
}

export interface Change {

    [propName: string]: any;

    changeType: string;

    html?: Observable<string>; // for dashboard
}

export interface Staging {

    applicationId: string;

    sourceEnvironmentId: string;

    targetEnvironmentId: string;

    changes: Change[];
}

export interface StagingResult {

    change: Change;

    stagingSuccessful: boolean;

    errorMessage: string;

}

export interface ChangelogEntry {

    id: string;

    timestamp: string;

    principal: string;

    principalFullName?: string;

    change: Change;

}

@Injectable()
export class EnvironmentsService {

    private currentEnvironment = new ReplaySubject<KafkaEnvironment>(1);

    private environments = new ReplayContainer<KafkaEnvironment[]>(() => this.http.get('/api/environments'));

    private servers = new ReplayContainer<EnvironmentServerInfo[]>(() => EMPTY);

    constructor(private http: HttpClient, toasts: ToastService) {
        this.getEnvironments().pipe(take(1)).toPromise().then(envs => {
            if (envs.length) {
                const userEnv = localStorage.getItem(LOCAL_STORAGE_ENV_KEY) || envs[0].id;
                this.setCurrentEnvironment(envs.find(env => env.id === userEnv) || envs[0]);
            }
        }, () => toasts.addErrorToast('Kommunikation mit dem Backend schlug fehl.'));
    }

    public getCurrentEnvironment(): Observable<KafkaEnvironment> {
        return this.currentEnvironment;
    }

    public setCurrentEnvironment(environment: KafkaEnvironment) {
        this.currentEnvironment.next(environment);
        localStorage.setItem(LOCAL_STORAGE_ENV_KEY, environment.id);
        this.servers.setRefresher(() => this.http.get('/api/environments/' + environment.id));
        this.servers.next([]);
        this.servers.refresh();
    }

    public getEnvironments(): Observable<KafkaEnvironment[]> {
        return this.environments.getObservable();
    }

    public getCurrentEnvironmentServerInfo(): Observable<EnvironmentServerInfo[]> {
        return this.servers.getObservable();
    }

    public getChangeLog(environmentId: string): Observable<ChangelogEntry[]> {
        return this.http.get('/api/environments/' + environmentId + '/changelog?limit=100').pipe(map(d => d as ChangelogEntry[]));
    }

    public getRegisteredApplications(environmentId: string): Promise<ApplicationInfo[]> {
        return this.http.get<ApplicationInfo[]>('/api/registered-applications/' + environmentId).toPromise();

    }

    public prepareStaging(applicationId: string, environment: KafkaEnvironment): Promise<Staging> {
        return this.http.get('/api/environments/' + environment.id + '/staging/' + applicationId)
            .pipe(map(data => data as Staging)).toPromise();
    }

    public performStaging(applicationId: string, environment: KafkaEnvironment, selectedChanges: Change[]): Promise<StagingResult[]> {
        const body = JSON.stringify(selectedChanges);
        console.log(selectedChanges);
        return this.http.post('/api/environments/' + environment.id + '/staging/' + applicationId, body, { headers: jsonHeader() })
            .pipe(map(data => data as StagingResult[])).toPromise();
    }

    public getFrameworkConfigTemplate(environmentId: string, framework: string): Observable<string> {
        return this.http.get('/api/util/framework-config/' + environmentId + '/' + framework, { responseType: 'text' });
    }

}

