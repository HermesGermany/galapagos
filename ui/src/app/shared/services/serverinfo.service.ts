import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ReplayContainer } from './services-common';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';


export interface AppInfo {
    version: string;
}

export interface TogglesInfo {
    subscriptionApproval: string;

    schemaDeleteWithSub: string;
}

export interface ServerInfo {
    app: AppInfo;

    toggles?: TogglesInfo;

    galapagos?: {
        instanceName: string;
    };
}

export interface CustomLink {
    id: string;
    href: string;
    label: string;
    linkType: 'EDUCATIONAL' | 'SOURCECODE' | 'OTHER';
}

export interface UiConfig {
    changelogEntries: number;
    changelogMinDays: number;
    minDeprecationTime: {
        years: number;
        months: number;
        days: number;
    };
    customLinks: CustomLink[];

    profilePicture: string;
    defaultPicture: string;
    customImageUrl?: string;
}

@Injectable()
export class ServerInfoService {

    private serverInfo = new ReplayContainer<ServerInfo>(() => this.http.get('/actuator/info'));

    private uiConfig = new ReplayContainer<UiConfig>(() => this.http.get('/api/util/uiconfig'));

    constructor(private http: HttpClient) {
    }

    public getServerInfo(): Observable<ServerInfo> {
        return this.serverInfo.getObservable();
    }

    public getUiConfig(): Observable<UiConfig> {
        return this.uiConfig.getObservable();
    }

    public getKafkaVersion(environmentId: string): Observable<string> {
        return this.http.get('/api/environments/' + environmentId + '/kafkaVersion', { responseType: 'text' }).pipe(map(s => s as string));
    }

}
