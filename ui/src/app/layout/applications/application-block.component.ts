import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { UserApplicationInfoWithTopics } from './applications.component';
import { combineLatest, Observable, of } from 'rxjs';
import { catchError, map, shareReplay, startWith } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ReplayContainer } from '../../shared/services/services-common';
import { ApiKeyService, ApplicationApiKey, ApplicationApikeyAuthData } from '../../shared/services/apikey.service';
import { ApplicationCertificate, CertificateService } from '../../shared/services/certificates.service';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';

export interface OpenApiKeyDialogEvent {
    application: UserApplicationInfoWithTopics;

    environment: KafkaEnvironment;
}

export interface OpenCertificateDialogEvent {
    application: UserApplicationInfoWithTopics;

    environment: KafkaEnvironment;
}

@Component({
    selector: 'app-application-block',
    templateUrl: './application-block.component.html',
    styleUrls: ['./application-block.component.scss']
})
export class ApplicationBlockComponent implements OnChanges {

    @Input() application: UserApplicationInfoWithTopics;

    @Input() apiKey: string;

    @Input() authenticationMode: string;

    @Input() currentEnv: KafkaEnvironment;

    @Output() openApiKeyDialog = new EventEmitter<OpenApiKeyDialogEvent>();

    @Output() openCertificateDialog = new EventEmitter<OpenCertificateDialogEvent>();

    internalTopicPrefixes: Observable<string[]>;

    consumerGroupPrefixes: Observable<string[]>;

    transactionIdPrefixes: Observable<string[]>;

    currentEnvApplicationApiKey: Observable<ApplicationApiKey>;

    applicationApiKeys: ReplayContainer<ApplicationApikeyAuthData>;

    currentEnvApplicationCertificate: Observable<ApplicationCertificate>;

    expiryDateString: Observable<string>;

    constructor(private apiKeyService: ApiKeyService, public environmentsService: EnvironmentsService,
                private certificateService: CertificateService, private translateService: TranslateService) {
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.application) {
            const change = changes.application;
            if (change.currentValue) {
                const app = change.currentValue as UserApplicationInfoWithTopics;
                this.buildPrefixes(app);

                if (this.authenticationMode === 'certificates') {
                    const applicationCertificates = this.certificateService.getApplicationCertificates(app.id);
                    this.currentEnvApplicationCertificate = combineLatest([applicationCertificates.getObservable(),
                        this.environmentsService.getCurrentEnvironment()]).pipe(map(
                        ([certs, env]) => certs['authentications'][env.id]));

                    const currentLang = this.translateService.onLangChange.pipe(map(evt => evt.lang))
                        .pipe(startWith(this.translateService.currentLang)).pipe(shareReplay(1));
                    this.expiryDateString = combineLatest([currentLang, this.currentEnvApplicationCertificate]).pipe(
                        map(([lang, cert]) => moment(cert['authentication'].expiresAt).locale(lang.toString()).format('L')));
                }

                if (this.authenticationMode === 'ccloud') {
                    const extractApiKey = (keys: ApplicationApikeyAuthData, env: KafkaEnvironment): ApplicationApiKey => {
                        if (!keys.authentications[env.id]) {
                            return null;
                        }
                        if (keys.authentications[env.id].authenticationType !== 'ccloud') {
                            return null;
                        }

                        return keys.authentications[env.id].authentication as ApplicationApiKey;
                    };

                    this.applicationApiKeys = this.apiKeyService.getApplicationApiKeys(app.id);
                    this.currentEnvApplicationApiKey = combineLatest([this.applicationApiKeys.getObservable(),
                        this.environmentsService.getCurrentEnvironment()]).pipe(map(
                        ([keys, env]) => extractApiKey(keys, env)
                    ));
                }
            } else {
                this.internalTopicPrefixes = of([]);
                this.consumerGroupPrefixes = of([]);
                this.transactionIdPrefixes = of([]);
                this.currentEnvApplicationApiKey = null;
                this.currentEnvApplicationCertificate = null;
            }
        }
    }

    private buildPrefixes(app: UserApplicationInfoWithTopics) {
        let prefixes = app.prefixes.pipe(shareReplay(1));
        // handle 404
        prefixes = prefixes.pipe(catchError(err => {
            if (err.status === 404) {
                return of({
                    internalTopicPrefixes: [],
                    consumerGroupPrefixes: [],
                    transactionIdPrefixes: []
                });
            }
            throw err;
        }));

        this.internalTopicPrefixes = prefixes.pipe(map(p => p.internalTopicPrefixes));
        this.consumerGroupPrefixes = prefixes.pipe(map(p => p.consumerGroupPrefixes));
        this.transactionIdPrefixes = prefixes.pipe(map(p => p.transactionIdPrefixes));
    }
}
