import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { ApplicationInfo, ApplicationOwnerRequest, ApplicationsService } from '../../shared/services/applications.service';
import { combineLatest, Observable } from 'rxjs';

import { map } from 'rxjs/operators';
import { KeycloakService } from 'keycloak-angular';
import { SortEvent } from './sortable.directive';
import { toNiceTimestamp } from 'src/app/shared/util/time-util';

interface TranslatedApplicationOwnerRequest extends ApplicationOwnerRequest {
    applicationName?: string;
    applicationInfoUrl?: string;
}

// TODO I think this could be moved to applicationService
const translateApps: (requests: ApplicationOwnerRequest[], apps: ApplicationInfo[]) => TranslatedApplicationOwnerRequest[] =
    (requests, apps) => {
        const appMap = { };
        apps.forEach(app => appMap[app.id] = app);
        return requests.map(req => appMap[req.applicationId] ? { ...req, applicationName: appMap[req.applicationId].name || req.id,
            applicationInfoUrl: appMap[req.applicationId].infoUrl } : req);
    };

const entityMap = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    '\'': '&#39;',
    '/': '&#x2F;'
};

@Component({
    selector: 'app-admin',
    templateUrl: './admin.component.html',
    styleUrls: ['./admin.component.scss'],
    animations: [routerTransition()]
})
export class AdminComponent implements OnInit {

    isAdmin = false;

    allRequests: Observable<TranslatedApplicationOwnerRequest[]>;

    searchTerm: string;

    constructor(private applicationsService: ApplicationsService, private keycloakService: KeycloakService) { }

    ngOnInit() {
        this.isAdmin = this.keycloakService.getUserRoles().indexOf('admin') > -1;
        // TODO move this to applicationService, for all and for user requests
        this.allRequests = combineLatest([this.applicationsService.getAllApplicationOwnerRequests(),
            this.applicationsService.getAvailableApplications(false)]).pipe(map(values => translateApps(values[0], values[1])))
            .pipe(map(values => values.map(req => this.escapeComments(req))));

        this.applicationsService.refresh().then();
    }

    approve(request: TranslatedApplicationOwnerRequest): Promise<any> {
        return this.applicationsService.updateApplicationOwnerRequest(request.id, 'APPROVED');
    }

    reject(request: TranslatedApplicationOwnerRequest): Promise<any> {
        const newStatus = request.state === 'SUBMITTED' ? 'REJECTED' : 'REVOKED';
        return this.applicationsService.updateApplicationOwnerRequest(request.id, newStatus);
    }

    onSort({ column, direction }: SortEvent) {
        // TODO
    }

    lastChangeTitle(request: TranslatedApplicationOwnerRequest): string {
        return this.niceTimestamp(request.lastStatusChangeAt) + ' by ' + request.lastStatusChangeBy;
    }

    niceTimestamp(str: string) {
        return toNiceTimestamp(str);
    }

    escapeComments(req: TranslatedApplicationOwnerRequest) {
        let comments = req.comments;
        if (comments) {
            comments = this.escapeHtml(comments);
            comments = comments.replace(/(?:\r\n|\r|\n)/g, '<br>');
        }
        return {
            ...req,
            comments: comments
        };
    }

    escapeHtml(source: string) {
        return String(source).replace(/[&<>"'\/]/g, s => entityMap[s]);
    }
}
