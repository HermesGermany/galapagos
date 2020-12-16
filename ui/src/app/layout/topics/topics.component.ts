import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { routerTransition } from '../../router.animations';
import { Topic, TopicsService } from '../../shared/services/topics.service';
import { Observable } from 'rxjs';
import { SORT_ASC, SortController, SortDirection } from './sort';
import { FilterController } from './filter';

interface SearchData {
    searchTerm: string;

    showInternalTopics: boolean;
}

@Component({
    selector: 'app-topics',
    templateUrl: './topics.component.html',
    styleUrls: ['./topics.component.scss'],
    encapsulation: ViewEncapsulation.None,
    animations: [routerTransition()]
})
export class TopicsComponent implements OnInit {

    searchData: SearchData = {
        searchTerm: '',
        showInternalTopics: false
    };

    topics: Observable<Topic[]>;

    loadingTopics: Observable<Boolean>;

    private sortController = new SortController<Topic>(this.compareTopics);

    private filterController = new FilterController<Topic, SearchData>(this.filterTopic, this.searchData);

    constructor(private topicsService: TopicsService) {
    }

    ngOnInit() {
        this.loadingTopics = this.topicsService.listTopics().getLoadingStatus();

        this.topics = this.sortController.sort(this.filterController.filter(this.topicsService.listTopics().getObservable()));

        this.topicsService.listTopics().refresh().then();
    }

    sortTopics(event: any) {
        this.sortController.addSort(event.column, event.direction);
    }

    searchTopics() {
        this.filterController.setSearchData(this.searchData);
    }


    private compareTopics(a: Topic, b: Topic, column: string, direction: SortDirection) {
        const dirFactor = direction === SORT_ASC ? 1 : -1;
        switch (column) {
            case 'name':
                return a.name.localeCompare(b.name) * dirFactor;
            case 'type':
                return a.topicType.localeCompare(b.topicType) * dirFactor;
            case 'ownerApplication':
                const app1 = a.ownerApplication;
                const app2 = b.ownerApplication;
                if (!app1 && !app2) {
                    return 0;
                }
                if (!app1) {
                    return -1 * dirFactor;
                }
                if (!app2) {
                    return dirFactor;
                }
                return app1.name.localeCompare(app2.name) * dirFactor;
        }

        return 0;
    }

    private filterTopic(topic: Topic, searchData: SearchData): boolean {
        if (!searchData) {
            return true;
        }

        if (topic.topicType === 'INTERNAL' && !searchData.showInternalTopics) {
            return false;
        }

        let searchText = searchData.searchTerm;
        if (!searchText || !searchText.length) {
            return true;
        }

        searchText = searchText.toLowerCase();

        return topic.name.toLowerCase().indexOf(searchText) > -1
            || (topic.description &&  topic.description.toLowerCase().indexOf(searchText) > -1)
            || (topic.ownerApplication && topic.ownerApplication.name.toLowerCase().indexOf(searchText) > -1);
    }
}
