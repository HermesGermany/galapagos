import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataSettingsComponent } from './data-settings.component';

describe('DataSettingsComponent', () => {
    let component: DataSettingsComponent;
    let fixture: ComponentFixture<DataSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [DataSettingsComponent]
        })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(DataSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
