import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastService } from '../../shared/modules/toast/toast.service';
import { ApplicationsService } from '../../shared/services/applications.service';
import { ServerInfoService } from '../../shared/services/serverinfo.service';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../shared';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { LanguageTranslationModule } from '../../shared/modules/language-translation/language-translation.module';
import { SingleTopicComponent } from './single-topic.component';
import { RouterModule } from '@angular/router';
import { TopicsService } from '../../shared/services/topics.service';
import { CertificateService } from '../../shared/services/certificates.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { SpinnerWhileDirective } from '../../shared/modules/spinner-while/spinner-while.directive';
import { Highlight } from 'ngx-highlightjs';
import { of } from 'rxjs';
import { ReplayContainer } from '../../shared/services/services-common';
import { AuthService } from '../../shared/services/auth.service';
import { MockAuthService } from '../../shared/util/test-util';

describe('SingleTopicComponent', () => {
    let component: SingleTopicComponent;
    let fixture: ComponentFixture<SingleTopicComponent>;
    let testTopic: {
        eolDate: string;
        environmentId: string;
        subscriptionApprovalRequired: boolean;
        createdTimestamp: string;
        deprecated: boolean;
        name: string;
        ownerApplication: { aliases: string[]; name: string; id: string };
        deletable: boolean;
        description: string;
        deprecationText: string;
        topicType: string;
    };
    let deprecationText: string;
    let eolDate: string;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                RouterTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule,
                FormsModule,
                CommonModule
            ],
            declarations: [SingleTopicComponent, SpinnerWhileDirective, Highlight],
            providers: [
                { provide: AuthService, useClass: MockAuthService },
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

        fixture = TestBed.createComponent(SingleTopicComponent);
        component = fixture.componentInstance;

        testTopic = {
            name: 'myCoolTopic',

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

        deprecationText = 'this topic should not be used';
        eolDate = '2029-01-01';
    });


    it('should create single topic Component', () => {
        expect(component).toBeTruthy();
    });

    it('should display deprecation of topic', (() => {
        const topicsService = fixture.debugElement.injector.get(TopicsService);
        const spy: jasmine.Spy = spyOn(topicsService, 'listTopics')
            .and.returnValue(new ReplayContainer(() => of([testTopic])));

        spyOn(topicsService, 'deprecateTopic').and.returnValue(Promise.resolve({
            ...testTopic,
            deprecated: true,
            deprecationText: deprecationText,
            eolDate: eolDate
        }));

        const updatedTopic = topicsService.deprecateTopic(deprecationText, eolDate, testTopic.name);
        fixture.detectChanges();

        expect(spy).toHaveBeenCalled();
        expect(spy.calls.all().length).toBe(1);
        updatedTopic.then(topicData => {
            expect(topicData.deprecated).toBeTruthy();
            expect(topicData.deprecationText).toEqual('this topic should not be used');
            expect(topicData.eolDate).toEqual('2029-01-01');
        });
    }));
});
