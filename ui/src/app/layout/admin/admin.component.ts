import { Component, OnInit } from '@angular/core';
import { routerTransition } from '../../router.animations';
import {
    ApplicationInfo,
    ApplicationOwnerRequest,
    ApplicationsService
} from '../../shared/services/applications.service';
import { combineLatest, Observable, tap } from 'rxjs';

import { map } from 'rxjs/operators';
import { SortEvent } from './sortable.directive';
import { toNiceTimestamp } from 'src/app/shared/util/time-util';
import { TranslateService } from '@ngx-translate/core';

import { NgbPaginationConfig } from '@ng-bootstrap/ng-bootstrap';
import { SortDirection } from '../topics/sort';
import { AuthService } from '../../shared/services/auth.service';

interface TranslatedApplicationOwnerRequest extends ApplicationOwnerRequest {
    applicationName?: string;
    applicationInfoUrl?: string;
}


interface State {
    currentPage: number;
    totalItems: number;
    pageSize: number;
    maxSize: number;
    searchTerm: string;
    sortColumn: string;
    sortDirection: SortDirection;
}

// TODO I think this could be moved to applicationService
const translateApps: (requests: ApplicationOwnerRequest[], apps: ApplicationInfo[]) => TranslatedApplicationOwnerRequest[] =
    (requests, apps) => {
        const appMap = {};
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

    isAdmin: Observable<boolean>;

    currentRequests: TranslatedApplicationOwnerRequest[];

    allFetchedRequests: TranslatedApplicationOwnerRequest[];

    state: State = {
        currentPage: 1, // Current page number
        pageSize: 15, // Number of items per page
        maxSize: 5, // Maximum number of page links to display
        totalItems: 0, // Total number of items
        searchTerm: '',
        sortColumn: '',
        sortDirection: ''
    };

    constructor(private applicationsService: ApplicationsService, authService: AuthService,
                private translate: TranslateService, private config: NgbPaginationConfig) {
        config.size = 'sm';
        config.boundaryLinks = true;
        this.isAdmin = authService.admin;
    }

    async ngOnInit() {
        // TODO move this to applicationService, for all and for user requests
        const allRequests = combineLatest([this.applicationsService.getAllApplicationOwnerRequests(),
            this.applicationsService.getAvailableApplications(false)]).pipe(map(values => translateApps(values[0], values[1])))
            .pipe(map(values => values.map(req => this.escapeComments(req))));

        allRequests.pipe(tap(requests => {
            this.allFetchedRequests = requests;
            this.state.totalItems = requests.length;
            this.sliceData();
        })).subscribe();

        this.applicationsService.refresh().then();
    }

    approve(request: TranslatedApplicationOwnerRequest): Promise<any> {
        return this.applicationsService.updateApplicationOwnerRequest(request.id, 'APPROVED');
    }

    reject(request: TranslatedApplicationOwnerRequest): Promise<any> {
        const newStatus = request.state === 'SUBMITTED' ? 'REJECTED' : 'REVOKED';
        return this.applicationsService.updateApplicationOwnerRequest(request.id, newStatus);
    }

    async onSort({ column, direction }: SortEvent) {
        const requests = this.allFetchedRequests;
        if (direction === 'asc') {
            this.allFetchedRequests = requests.sort((a, b) => a[column] < b[column] ? 1 : a[column] > b[column] ? -1 : 0);
        }
        if (direction === 'desc') {
            this.allFetchedRequests = requests.sort((a, b) => a[column] > b[column] ? 1 : a[column] < b[column] ? -1 : 0);
        }
        this.sliceData();
    }

    sliceData() {
        this.currentRequests = this.allFetchedRequests.slice(
            (this.state.currentPage-1)*this.state.pageSize, this.state.currentPage*this.state.pageSize);
    }

    lastChangeTitle(request: TranslatedApplicationOwnerRequest): Observable<string> {
        return this.niceTimestamp(request.lastStatusChangeAt).pipe(map(l => l + ' by ' + request.lastStatusChangeBy));
    }

    niceTimestamp(str: string): Observable<string> {
        return toNiceTimestamp(str, this.translate);
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

    matches(request: TranslatedApplicationOwnerRequest, searchTerm: string) {
        return (
            (request.applicationName && request.applicationName.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (request.applicationId && request.applicationId.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (request.userName && request.userName.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (request.comments && request.comments.toLowerCase().includes(searchTerm.toLowerCase()))
        );
    }

    search() {
        const { pageSize, searchTerm } = this.state;
        const filterData = this.allFetchedRequests.filter(request => this.matches(request, searchTerm));
        this.state.totalItems = filterData.length;
        this.currentRequests = filterData.slice(0, pageSize);
    }

}
