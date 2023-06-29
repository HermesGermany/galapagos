import { Injectable } from '@angular/core';
import { EMPTY, firstValueFrom, Observable, ReplaySubject, tap } from 'rxjs';
import { jsonHeader, ReplayContainer } from './services-common';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { ToastService } from '../modules/toast/toast.service';

const LOCAL_STORAGE_ENV_KEY = 'galapagos.environment';

export interface KafkaEnvironment {

    id: string;

    name: string;

    bootstrapServers: string;

    production: boolean;

    stagingOnly: boolean;

    authenticationMode: string;
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

    profilePictureUrl: string;

    defaultPictureUrl: string;

}

@Injectable()
export class EnvironmentsService {

    private currentEnvironment = new ReplaySubject<KafkaEnvironment>(1);

    private environments = new ReplayContainer<KafkaEnvironment[]>(() => this.http.get('/api/environments'));

    private servers = new ReplayContainer<EnvironmentServerInfo[]>(() => EMPTY);


    constructor(private http: HttpClient, toasts: ToastService) {
        firstValueFrom(this.getEnvironments()).then(envs => {
            if (envs.length) {
                const userEnv = localStorage.getItem(LOCAL_STORAGE_ENV_KEY) || envs[0].id;
                this.setCurrentEnvironment(envs.find(env => env.id === userEnv) || envs[0]);
            }
        }, () => toasts.addErrorToast('Kommunikation mit dem Backend schlug fehl.'));
    }

    public getCurrentEnvironment(): Observable<KafkaEnvironment> {
        return this.currentEnvironment;
    }

    public getNextStage(environment: KafkaEnvironment): Promise<string> {
        return firstValueFrom(
            this.http.get<{ nextStage: string }>('/api/environments/' + environment.id + '/next-stage')
                .pipe(map(r => r.nextStage))
        );
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
        return this.http.get('/api/environments/' + environmentId + '/changelog?limit=1000').pipe(map(d => d as ChangelogEntry[]));
    }

    public prepareStaging(applicationId: string, environment: KafkaEnvironment): Promise<Staging> {
        return firstValueFrom(this.http.get('/api/environments/' + environment.id + '/staging/' + applicationId)
            .pipe(map(data => data as Staging)));
    }

    public performStaging(applicationId: string, environment: KafkaEnvironment, selectedChanges: Change[]): Promise<StagingResult[]> {
        const body = JSON.stringify(selectedChanges);
        return firstValueFrom(this.http.post('/api/environments/' + environment.id + '/staging/' + applicationId, body
            , { headers: jsonHeader() })
            .pipe(map(data => data as StagingResult[])));
    }

    public getFrameworkConfigTemplate(environmentId: string, framework: string): Observable<string> {
        return this.http.get('/api/util/framework-config/' + environmentId + '/' + framework, { responseType: 'text' });
    }

}

