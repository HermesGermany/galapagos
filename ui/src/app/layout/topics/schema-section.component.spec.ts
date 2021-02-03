import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SchemaSectionComponent } from './schema-section.component';
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

describe('SchemaSectionComponent', () => {
    let component: SchemaSectionComponent;
    let fixture: ComponentFixture<SchemaSectionComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SchemaSectionComponent],
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
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(SchemaSectionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
