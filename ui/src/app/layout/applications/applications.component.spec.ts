import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TopicsService } from '../../shared/services/topics.service';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { CertificateService } from '../../shared/services/certificates.service';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../shared/modules';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ApplicationsComponent } from './applications.component';
import { SpinnerWhileModule } from '../../shared/modules/spinner-while/spinner-while.module';
import { ReplayContainer } from '../../shared/services/services-common';
import { of } from 'rxjs';
import { OpensslCommandModule } from '../../shared/modules/openssl-command/openssl-command.module';

describe('ApplicationsComponent', () => {

    let component: ApplicationsComponent;
    let fixture: ComponentFixture<ApplicationsComponent>;
    let apps;
    let testTopic;

    beforeEach((() => {
        TestBed.configureTestingModule({
            declarations: [ApplicationsComponent],
            imports: [
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule,
                FormsModule,
                CommonModule,
                SpinnerWhileModule,
                OpensslCommandModule
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
                NgbModal
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ApplicationsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();

        apps = [{
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
            }]
        }];
        testTopic = {
            name: 'myCoolTopic',

            topicType: 'EVENTS',

            environmentId: 'prod',

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

    }));

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should only disable second radio button on certificate dialog on prod stage', waitForAsync(() => {

        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const certificateService = fixture.debugElement.injector.get(CertificateService);
        const applicationsService = fixture.debugElement.injector.get(ApplicationsService);

        const applicationsSpy: jasmine.Spy = spyOn(applicationsService, 'getUserApplications')
            .and.returnValue(new ReplayContainer(() => of([apps])));

        const topicSpy: jasmine.Spy = spyOn(topicsService, 'listTopics')
            .and.returnValue(new ReplayContainer(() => of([testTopic])));

        const certificateSpy: jasmine.Spy = spyOn(certificateService, 'getApplicationCertificates')
            .and.returnValue(Promise.resolve([{
                environmentId: 'prod',
                dn: 'CN=My User;OU= A ou',
                certificateDownloadUrl: 'url goes here',
                expiresAt: 'some day'
            }]));

        component.userApplications = of(apps);

        component.environments = of([{
            id: 'devtest',
            name: 'devtest',
            bootstrapServers: 'myBootstrapServers',
            production: false,
            stagingOnly: false
        }, {
            id: 'prod',
            name: 'prod',
            bootstrapServers: 'myBootstrapServers2',
            production: true,
            stagingOnly: true
        }]);


        fixture.detectChanges();
        component.ngOnInit();

        const button = document.querySelector('#prodButton') as HTMLInputElement;
        button.click();

        fixture.whenStable().then(() => {

            const csrCheckBox = document.querySelector('#csrRadio') as HTMLInputElement;
            const noCsrCheckBox = document.querySelector('#noCsrRadio') as HTMLInputElement;
            noCsrCheckBox.click();

            expect(applicationsSpy).toHaveBeenCalled();
            expect(topicSpy).toHaveBeenCalled();
            expect(certificateSpy).toHaveBeenCalled();
            expect(csrCheckBox.checked).toBeTruthy();
            expect(noCsrCheckBox.checked).toBeFalsy();

        });

    }));

    it('should be swichtable to csr mode on prod dialog right after creating certificate on dev stage', waitForAsync(() => {

        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const certificateService = fixture.debugElement.injector.get(CertificateService);
        const applicationsService = fixture.debugElement.injector.get(ApplicationsService);

        const applicationsSpy: jasmine.Spy = spyOn(applicationsService, 'getUserApplications')
            .and.returnValue(new ReplayContainer(() => of([apps])));

        const topicSpy: jasmine.Spy = spyOn(topicsService, 'listTopics')
            .and.returnValue(new ReplayContainer(() => of([testTopic])));

        const certificateSpy: jasmine.Spy = spyOn(certificateService, 'getApplicationCertificates')
            .and.returnValue(Promise.resolve([{
                environmentId: 'prod',
                dn: 'CN=My User;OU= A ou',
                certificateDownloadUrl: 'url goes here',
                expiresAt: 'some day'
            }]));

        component.userApplications = of(apps);

        component.environments = of([{
            id: 'devtest',
            name: 'devtest',
            bootstrapServers: 'myBootstrapServers',
            production: false,
            stagingOnly: false
        }, {
            id: 'prod',
            name: 'prod',
            bootstrapServers: 'myBootstrapServers2',
            production: true,
            stagingOnly: true
        }]);

        fixture.detectChanges();
        component.ngOnInit();

        // simulate generating certificate on dev
        const devButton = document.querySelector('#devButton') as HTMLInputElement;
        devButton.click();
        const generateCertButton = document.querySelector('#certButton') as HTMLInputElement;
        generateCertButton.click();

        const prodButton = document.querySelector('#prodButton') as HTMLInputElement;
        prodButton.click();

        fixture.whenStable().then(() => {

            const csrCheckBox = document.querySelector('#csrRadio') as HTMLInputElement;
            csrCheckBox.click();

            expect(applicationsSpy).toHaveBeenCalled();
            expect(topicSpy).toHaveBeenCalled();
            expect(certificateSpy).toHaveBeenCalled();
            expect(csrCheckBox.checked).toBeTruthy();

        });

    }));

});
