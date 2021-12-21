import { Component, Input } from '@angular/core';
import { TopicType } from '../../../shared/services/topics.service';
import { KafkaEnvironment } from '../../../shared/services/environments.service';
import * as moment from 'moment';
import { ChangeContext, Options } from '@angular-slider/ngx-slider';

type CleanUpStrategy = 'compact' | 'delete';

type Criticality = 'NORMAL' | 'CRITICAL';

type MessagesPerDay = 'FEW' | 'NORMAL' | 'MANY' | 'VERY_MANY';

type MessagesSize = 'VERY_SMALL' | 'SMALL' | 'NORMAL' | 'LARGE' | 'VERY_LARGE';

export interface TopicSettingsData {

    subscriptionApprovalRequired: boolean;

    cleanUpStrategy: CleanUpStrategy[];

    compactionTimeMillis: number;

    retentionTimeMillis: number;

    criticality: Criticality;

    messagesPerDay: MessagesPerDay;

    messagesSize: MessagesSize;

}

@Component({
    selector: 'app-data-settings',
    templateUrl: './data-settings.component.html',
    styleUrls: ['./data-settings.component.scss']
})
export class DataSettingsComponent {

    @Input() topicType: TopicType;

    @Input() selectedEnvironment: KafkaEnvironment;

    @Input() showSubscriptionApprovalRequired: boolean;

    subscriptionApprovalRequired = false;

    activeButtons = { logCompaction: false, deletion: false };

    selectedCleanUpStrategy: CleanUpStrategy[] = ['delete'];

    compactionTime = 24;

    compactionTimeUnit = 'h';

    retentionTime = 7;

    retentionTimeUnit = 'd';

    criticalityType: Criticality = 'NORMAL';

    dataIntervals: string[] = ['<1K', '<100K', '<1M', '>1M'];

    selectedDataSliderValue: MessagesPerDay;

    sizeIntervals: string[] = ['< 1 KB', '< 10 KB', '< 100 KB', '< 1 MB', '> 1 MB'];

    selectedSizeSliderValue;

    optionsData: Options = {
        stepsArray: [
            { value: 0 },
            { value: 1 },
            { value: 2 },
            { value: 3 },
            { value: 4 }
        ],
        step: 1,
        showTicks: true,
        showTicksValues: true,
        translate: (value: number): string => this.dataIntervals[value - 1]
    };

    optionsSize: Options = {
        stepsArray: [
            { value: 0 },
            { value: 1 },
            { value: 2 },
            { value: 3 },
            { value: 4 },
            { value: 5 }
        ],
        step: 1,
        showTicks: true,
        showTicksValues: true,

        translate: (value: number): string => this.sizeIntervals[value - 1]
    };

    constructor() {
    }

    deleteItem(deletion: string) {

        this.selectedCleanUpStrategy = this.selectedCleanUpStrategy.filter(strategy => strategy !== deletion);
    }

    handleSubscriptionApprovalRequiredChange(): void {

        if (this.selectedCleanUpStrategy.length === 1 && this.selectedCleanUpStrategy.includes('delete')) {
            return;
        }

        if (this.subscriptionApprovalRequired && !this.selectedCleanUpStrategy.includes('delete')) {
            this.selectedCleanUpStrategy.push('delete');
        }

    }

    prepareDataForParent(): TopicSettingsData {
        const initialSettings: TopicSettingsData = {
            subscriptionApprovalRequired: this.subscriptionApprovalRequired,
            cleanUpStrategy: this.selectedCleanUpStrategy,
            compactionTimeMillis: this.toMilliSeconds(Number(this.compactionTime), this.compactionTimeUnit),
            retentionTimeMillis: this.toMilliSeconds(Number(this.retentionTime), this.retentionTimeUnit),
            criticality: this.criticalityType,
            messagesPerDay: this.selectedDataSliderValue,
            messagesSize: this.selectedSizeSliderValue
        };

        return initialSettings;
    }

    onUserChangeEndData(changeContext: ChangeContext) {

        this.selectedDataSliderValue = this.resolveData(changeContext.value);
    }

    onUserChangeEndSize(changeContext: ChangeContext) {

        this.selectedSizeSliderValue = this.resolveSize(changeContext.value);
    }

    private toMilliSeconds(time, unit) {
        return moment.duration(time, unit).asMilliseconds();
    }

    private resolveData(index: number): MessagesPerDay {
        switch (index) {
            case 1:
                return 'FEW';
            case 2:
                return 'NORMAL';
            case 3:
                return 'MANY';
            case 4:
                return 'VERY_MANY';
        }
    }

    private resolveSize(index: number): MessagesSize {
        switch (index) {
            case 1:
                return 'VERY_SMALL';
            case 2:
                return 'SMALL';
            case 3:
                return 'NORMAL';
            case 4:
                return 'LARGE';
            case 5:
                return 'VERY_LARGE';
        }
    }

}
