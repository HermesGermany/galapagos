import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TopicsService } from '../../shared/services/topics.service';
import { EnvironmentsService } from '../../shared/services/environments.service';
import { ApplicationsService } from '../../shared/services/applications.service';
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
import { of } from 'rxjs';
import { ApiKeyService } from '../../shared/services/apikey.service';

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

});
