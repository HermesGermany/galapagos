import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { SchemaSectionComponent } from './schema-section.component';
import { RouterModule } from '@angular/router';
import { Topic, TopicsService } from '../../shared/services/topics.service';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { CertificateService } from '../../shared/services/certificates.service';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../shared/modules';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { Location } from '@angular/common';
import { LoginComponent } from '../../login/login.component';

describe('SchemaSectionComponent', () => {
    let component: SchemaSectionComponent;
    let fixture: ComponentFixture<SchemaSectionComponent>;
    let topic: Topic;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SchemaSectionComponent],
            imports: [
                TranslateModule.forRoot(),
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule
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
                LoginComponent
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

            deletable: false
        };
        component.topic = of(topic);

    });


    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should show delete schema button if toggle is set to true and we are on dev stage', (fakeAsync(() => {
        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);
        const serverInfoService = fixture.debugElement.injector.get(ServerInfoService);
        const serviceSpy: jasmine.Spy = spyOn(topicsService, 'getTopicSchemas').and.returnValue(Promise.resolve([{
            id: '123',
            topicName: 'myTopic',
            schemaVersion: 1,
            jsonSchema: '{}',
            isLatest: false
        }, {
            id: '1234',
            topicName: 'myTopic',
            schemaVersion: 2,
            jsonSchema: '{"e","f"}',
            isLatest: true
        }]));
        const envSpy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment')
            .and.returnValue(of({
                id: 'devtest',
                name: 'devtest',
                bootstrapServers: 'myBootstrapServers',
                production: false,
                stagingOnly: false
            }));

        const serverInfoSpy: jasmine.Spy = spyOn(serverInfoService, 'getServerInfo').and.returnValue(of({
            app: {
                version: 'local-dev'
            },
            toggles: {
                subscriptionApproval: 'false',
                schemaDeleteWithSub: 'true'
            }

        }));
        component.editSchemaMode = false;
        const debugElement = fixture.debugElement;

        component.loadSchemas(topic, 'devtest');

        fixture.detectChanges();
        expect(serviceSpy).toHaveBeenCalled();
        expect(envSpy).toHaveBeenCalled();
        expect(serverInfoSpy).toHaveBeenCalled();
        expect(debugElement.query(By.css('#schemaDeleteButton'))).toBeDefined();

    })));

    it('should not show delete schema button if toggle is set to false and we are on prod stage', (() => {
        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const environmentsService = fixture.debugElement.injector.get(EnvironmentsService);
        const serverInfoService = fixture.debugElement.injector.get(ServerInfoService);
        const serviceSpy: jasmine.Spy = spyOn(topicsService, 'getTopicSchemas').and.returnValue(Promise.resolve([{
            id: '123',
            topicName: 'myTopic',
            schemaVersion: 1,
            jsonSchema: '{}',
            isLatest: true
        }]));
        const envSpy: jasmine.Spy = spyOn(environmentsService, 'getCurrentEnvironment')
            .and.returnValue(of({
                id: 'devtest',
                name: 'devtest',
                bootstrapServers: 'myBootstrapServers',
                production: true,
                stagingOnly: true
            }));

        const serverInfoSpy: jasmine.Spy = spyOn(serverInfoService, 'getServerInfo').and.returnValue(of({
            app: {
                version: 'local-dev'
            },
            toggles: {
                subscriptionApproval: 'false',
                schemaDeleteWithSub: 'true'
            }

        }));
        const debugElement = fixture.debugElement;

        component.loadSchemas(topic, 'devtest');

        fixture.detectChanges();
        expect(serviceSpy).toHaveBeenCalled();
        expect(envSpy).toHaveBeenCalled();
        expect(serverInfoSpy).toHaveBeenCalled();
        expect(debugElement.query(By.css('#schemaDeleteButton'))).toBeNull();

    }));
});
