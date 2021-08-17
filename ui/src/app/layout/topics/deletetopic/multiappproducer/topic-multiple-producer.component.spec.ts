import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TopicMultipleProducerComponent } from './topic-multiple-producer.component';

describe('TopicMultipleProducerComponent', () => {
    let component: TopicMultipleProducerComponent;
    let fixture: ComponentFixture<TopicMultipleProducerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [TopicMultipleProducerComponent]
        })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TopicMultipleProducerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
