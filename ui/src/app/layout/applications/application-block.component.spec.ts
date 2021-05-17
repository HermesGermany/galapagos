import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TopicsService } from '../../shared/services/topics.service';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbAccordion, NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../shared/modules';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { SpinnerWhileModule } from '../../shared/modules/spinner-while/spinner-while.module';
import { of } from 'rxjs';
import { ApplicationBlockComponent } from './application-block.component';
import { SimpleChange } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ApiKeyService } from '../../shared/services/apikey.service';
import { ReplayContainer } from '../../shared/services/services-common';

describe('ApplicationBlockComponent', () => {

    let component: ApplicationBlockComponent;
    let fixture: ComponentFixture<ApplicationBlockComponent>;
    let app;

    beforeEach((() => {
        TestBed.configureTestingModule({
            declarations: [ApplicationBlockComponent],
            imports: [
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule,
                FormsModule,
                CommonModule,
                SpinnerWhileModule
            ],
            providers: [
                RouterModule,
                TopicsService,
                EnvironmentsService,
                ApplicationsService,
                ApiKeyService,
                ServerInfoService,
                ToastService,
                TranslateService,
                NgbModal
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ApplicationBlockComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();

        app = {
            id: '1',
            name: 'a nice application',
            aliases: ['app1'],
            owningTopics: ['myCoolTopic'],

            usingTopics: of(['some topic']),
            kafkaGroupPrefix: 'some prefix',
            businessCapabilities: [{
                id: '1',
                name: 'some name',
                topicNamePrefix: 'prefix name'
            }],
            prefixes: of({
                internalTopicPrefixes: ['a.internalTopic.prefix', 'another.internalTopic.prefix'],

                consumerGroupPrefixes: ['a.consumerGroup.prefix'],

                transactionIdPrefixes: ['a.transactionId.prefix']
            })
        };

    }));

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show correct Prefixes in table', (waitForAsync(() => {

        const apiKeyService = fixture.debugElement.injector.get(ApiKeyService);
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);

        const apiKeyServiceSpy: jasmine.Spy = spyOn(apiKeyService, 'getApplicationApiKeys')
            .and.returnValue(new ReplayContainer(() => of({
                authentications: {
                    prod: {
                        authenticationType: 'ccloud',
                        authentication: {
                            apiKey: 'myApiKey',
                            issuedAt: 'someDay',
                            userId: '1'
                        }
                    }
                }
            })));

        const envSpy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment')
            .and.returnValue(of({
                id: 'prod',
                name: 'prod',
                bootstrapServers: 'myBootstrapServers',
                production: true,
                stagingOnly: true
            }));

        component.application = app;

        component.ngOnChanges({
            application: new SimpleChange(undefined, app, false)
        });

        component.currentEnvApplicationApiKey = of({
            apiKey: 'myKey',
            issuedAt: 'some Day',
            userId: '1'
        });
        fixture.detectChanges();

        const accordion = fixture.debugElement.query(By.directive(NgbAccordion)).componentInstance;
        accordion.expand('_panel_rights');
        fixture.detectChanges();
        const listItemsInternal = document.getElementById('internalTopicPrefixes').childNodes;
        const listItemsTransactionIdPrefix = document.getElementById('transactionIdPrefixes').childNodes;
        const listItemsConsumerGroupPrefix = document.getElementById('consumerGroupPrefixes').childNodes;
        fixture.whenStable().then(() => {
            expect(apiKeyServiceSpy).toHaveBeenCalled();
            expect(envSpy).toHaveBeenCalled();
            expect(listItemsInternal.item(0).childNodes.item(0).textContent).toEqual('a.internalTopic.prefix');
            expect(listItemsInternal.item(1).childNodes.item(0).textContent).toEqual('another.internalTopic.prefix');
            expect(listItemsTransactionIdPrefix.item(0).childNodes.item(0).textContent).toEqual('a.transactionId.prefix');
            expect(listItemsConsumerGroupPrefix.item(0).childNodes.item(0).textContent).toEqual('a.consumerGroup.prefix');
        });

    })));

});
