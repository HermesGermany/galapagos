import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SchemaSectionComponent } from './schema-section.component';
import { RouterModule } from '@angular/router';
import { Topic, TopicsService } from '../../../shared/services/topics.service';
import { EnvironmentsService } from '../../../shared/services/environments.service';
import { ApplicationsService } from '../../../shared/services/applications.service';
import { CertificateService } from '../../../shared/services/certificates.service';
import { ServerInfoService } from '../../../shared/services/serverinfo.service';
import { ToastService } from '../../../shared/modules/toast/toast.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { LanguageTranslationModule } from '../../../shared/modules/language-translation/language-translation.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../../shared';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SpinnerWhileModule } from '../../../shared/modules/spinner-while/spinner-while.module';
import { AuthService } from '../../../shared/services/auth.service';
import { MockAuthService } from '../../../shared/util/test-util';

describe('SchemaSectionComponent', () => {
    let component: SchemaSectionComponent;
    let fixture: ComponentFixture<SchemaSectionComponent>;
    let topic: Topic;

    beforeEach((() => {
        TestBed.configureTestingModule({
            declarations: [SchemaSectionComponent],
            imports: [
                TranslateModule.forRoot(),
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule,
                FormsModule,
                SpinnerWhileModule
            ],
            providers: [
                RouterModule,
                TopicsService,
                EnvironmentsService,
                ApplicationsService,
                CertificateService,
                ServerInfoService,
                ToastService,
                TranslateService,
                NgbModal,
                Location,
                TranslateService,
                { provide: AuthService, useClass: MockAuthService }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(SchemaSectionComponent);
        component = fixture.componentInstance;

        topic = {
            name: 'myTopic',

            topicType: 'EVENTS',

            environmentId: 'devtest',

            description: 'my topic',

            ownerApplication: {
                id: '1',

                name: 'app1',

                aliases: ['a1']
            },

            createdTimestamp: 'string',

            deprecated: false,

            deprecationText: '',

            eolDate: '',

            subscriptionApprovalRequired: false,

            deletable: false,

            producers: []

        };
        component.topic = topic;
        const auth = fixture.debugElement.injector.get(AuthService);
        fixture.detectChanges();
    }));


    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show delete schema button if toggle is set to true and we are on dev stage', ((done: DoneFn) => {
        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);
        const serverInfoService = fixture.debugElement.injector.get(ServerInfoService);
        const serviceSpy: jasmine.Spy = spyOn(topicsService, 'getTopicSchemas').and.returnValue(Promise.resolve([{
            id: '123',
            topicName: 'myTopic',
            createdBy: 'someUser',
            createdAt: 'someTime',
            schemaVersion: 1,
            jsonSchema: '{}',
            isLatest: false,
            changeDescription: null
        }, {
            id: '1234',
            topicName: 'myTopic',
            createdBy: 'someUser2',
            createdAt: 'someTime2',
            schemaVersion: 2,
            jsonSchema: '{"e":"f"}',
            changeDescription: 'a change',
            isLatest: true
        }]));

        const envSpy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment')
            .and.returnValue(of({
                id: 'devtest',
                name: 'devtest',
                bootstrapServers: 'myBootstrapServers',
                production: false,
                stagingOnly: false,
                authenticationMode: 'certificates'
            }));

        const serverInfoSpy: jasmine.Spy = spyOn(serverInfoService, 'getServerInfo').and.returnValue(of({
            app: {
                version: 'local-dev'
            },
            toggles: {
                subscriptionApproval: 'false',
                schemaDeleteWithSub: 'true'

            },
            galapagos:{
                instanceName:'test-instance'
            }
        }));

        component.editSchemaMode = false;
        const debugElement = fixture.debugElement;

        component.ngOnInit();
        component.topicSubscribers = [];
        component.topicSchemas = Promise.resolve([
            {
                id: '123',
                topicName: 'myTopic',
                createdBy: 'someUser',
                createdAt: 'someTime',
                schemaVersion: 2,
                jsonSchema: '{}',
                changeDescription: '',
                isLatest: true
            }, {
                id: '1234',
                topicName: 'myTopic',
                createdBy: 'someUser2',
                createdAt: 'someTime2',
                schemaVersion: 1,
                jsonSchema: '{"e":"f"}',
                changeDescription: 'a change',
                isLatest: false
            }
        ]);

        component.isOwnerOfTopic = true;
        fixture.detectChanges();

        setTimeout(() => {
            fixture.detectChanges();
            expect(serviceSpy).toHaveBeenCalled();
            expect(envSpy).toHaveBeenCalled();
            expect(serverInfoSpy).toHaveBeenCalled();
            expect(debugElement.query(By.css('#schemaDeleteButton'))).toBeTruthy();
            done();
        }, 2000);
    }));

    it('should not show delete schema button if toggle is set to false and we are on prod stage', ((done: DoneFn) => {
        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);
        const serverInfoService = fixture.debugElement.injector.get(ServerInfoService);
        const serviceSpy: jasmine.Spy = spyOn(topicsService, 'getTopicSchemas').and.returnValue(Promise.resolve([{
            id: '123',
            topicName: 'myTopic',
            createdBy: 'someUser',
            createdAt: 'someTime',
            schemaVersion: 1,
            jsonSchema: '{}',
            isLatest: true,
            changeDescription: null
        }]));

        const envSpy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment')
            .and.returnValue(of({
                id: 'prod',
                name: 'prod',
                bootstrapServers: 'myBootstrapServers',
                production: true,
                stagingOnly: true,
                authenticationMode: 'certificates'
            }));

        const serverInfoSpy: jasmine.Spy = spyOn(serverInfoService, 'getServerInfo').and.returnValue(of({
            app: {
                version: 'local-dev'
            },
            toggles: {
                subscriptionApproval: 'false',
                schemaDeleteWithSub: 'false'
            },
            galapagos:{
                instanceName:'test-instance'
            }

        }));

        component.selectedEnvironment = of({
            id: 'prod',
            name: 'prod',
            bootstrapServers: 'myBootstrapServers',
            production: true,
            stagingOnly: true,
            authenticationMode: 'ccloud'
        });

        component.editSchemaMode = false;
        const debugElement = fixture.debugElement;

        component.ngOnInit();

        fixture.detectChanges();
        setTimeout(() => {
            fixture.detectChanges();
            expect(serviceSpy).toHaveBeenCalled();
            expect(envSpy).toHaveBeenCalled();
            expect(serverInfoSpy).toHaveBeenCalled();
            expect(debugElement.query(By.css('#schemaDeleteButton'))).toBeNull();
            done();
        }, 2000);
    }));

    it('should show button if toggle is set to false and we are on dev stage and there are no subscribers', ((done: DoneFn) => {
        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);
        const serverInfoService = fixture.debugElement.injector.get(ServerInfoService);
        const serviceSpy: jasmine.Spy = spyOn(topicsService, 'getTopicSchemas').and.returnValue(Promise.resolve([{
            id: '123',
            topicName: 'myTopic',
            schemaVersion: 1,
            jsonSchema: '{}',
            isLatest: true,
            changeDescription: null,
            createdBy: 'someUser',
            createdAt: 'someTime'
        }]));
        const envSpy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment')
            .and.returnValue(of({
                id: 'devtest',
                name: 'devtest',
                bootstrapServers: 'myBootstrapServers',
                production: false,
                stagingOnly: false,
                authenticationMode: 'certificates'
            }));

        const serverInfoSpy: jasmine.Spy = spyOn(serverInfoService, 'getServerInfo').and.returnValue(of({
            app: {
                version: 'local-dev'
            },
            toggles: {
                subscriptionApproval: 'false',
                schemaDeleteWithSub: 'false'
            },
            galapagos:{
                instanceName:'test-instance'
            }

        }));

        component.selectedEnvironment = of({
            id: 'devtest',
            name: 'devtest',
            bootstrapServers: 'myBootstrapServers',
            production: false,
            stagingOnly: false,
            authenticationMode: 'ccloud'
        });

        component.topicSubscribers = [];

        component.editSchemaMode = false;
        const debugElement = fixture.debugElement;
        component.topicSchemas = Promise.resolve([
            {
                id: '123',
                topicName: 'myTopic',
                createdBy: 'someUser',
                createdAt: 'someTime',
                schemaVersion: 2,
                jsonSchema: '{}',
                changeDescription: '',
                isLatest: true
            }, {
                id: '1234',
                topicName: 'myTopic',
                createdBy: 'someUser2',
                createdAt: 'someTime2',
                schemaVersion: 1,
                jsonSchema: '{"e":"f"}',
                changeDescription: 'a change',
                isLatest: false
            }
        ]);

        component.isOwnerOfTopic = true;

        component.ngOnInit();
        fixture.detectChanges();
        setTimeout(() => {
            fixture.detectChanges();
            expect(serviceSpy).toHaveBeenCalled();
            expect(envSpy).toHaveBeenCalled();
            expect(serverInfoSpy).toHaveBeenCalled();
            expect(debugElement.query(By.css('#schemaDeleteButton'))).toBeTruthy();
            done();
        }, 2000);
    }));
});
