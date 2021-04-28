import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { UserApplicationInfoWithTopics } from './applications.component';
import { combineLatest, Observable, of } from 'rxjs';
import { catchError, map, shareReplay } from 'rxjs/operators';
import { EnvironmentsService, KafkaEnvironment } from '../../shared/services/environments.service';
import { ReplayContainer } from '../../shared/services/services-common';
import { ApiKeyService, ApplicationApikey } from '../../shared/services/apikey.service';

export interface OpenApiKeyDialogEvent {
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

    @Output() openApiKeyDialog = new EventEmitter<OpenApiKeyDialogEvent>();

    currentEnvironment: string;

    internalTopicPrefixes: Observable<string[]>;

    consumerGroupPrefixes: Observable<string[]>;

    transactionIdPrefixes: Observable<string[]>;

    currentEnvApplicationApiKey: Observable<ApplicationApikey>;

    applicationApiKeys: ReplayContainer<ApplicationApikey[]>;

    constructor(private apiKeyService: ApiKeyService, public environmentsService: EnvironmentsService) {

        environmentsService.getCurrentEnvironment().subscribe({
            next: env => {
                this.currentEnvironment = env.id;
            }
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.application) {
            const change = changes.application;
            if (change.currentValue) {
                const app = change.currentValue as UserApplicationInfoWithTopics;
                this.buildPrefixes(app);

                this.applicationApiKeys = this.apiKeyService.getApplicationApiKeys(app.id, this.currentEnvironment);
                this.currentEnvApplicationApiKey = combineLatest([this.applicationApiKeys.getObservable(),
                    this.environmentsService.getCurrentEnvironment()]).pipe(map(
                    ([keys, env]) => keys.find(k => k.environmentId === env.id)));
            } else {
                this.internalTopicPrefixes = of([]);
                this.consumerGroupPrefixes = of([]);
                this.transactionIdPrefixes = of([]);
                this.currentEnvApplicationApiKey = null;
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
