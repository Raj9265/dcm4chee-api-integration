import {
    AfterContentChecked,
    Component,
    EventEmitter,
    Injector,
    Input,
    OnDestroy,
    OnInit,
    Output, ViewContainerRef
} from '@angular/core';
import {j4care} from '../j4care.service';
import * as _ from 'lodash-es';
import {AppService} from '../../app.service';
import {DeviceConfiguratorService} from '../../configuration/device-configurator/device-configurator.service';
import {DevicesService} from '../../configuration/devices/devices.service';
import {ConfirmComponent} from '../../widgets/dialogs/confirm/confirm.component';
import {RangePickerService} from '../../widgets/range-picker/range-picker.service';
import {StudyService} from '../../study/study/study.service';
import {MatDialog, MatDialogRef} from '@angular/material/dialog';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {TrimPipe} from '../../pipes/trim.pipe';
import {DcmDropDownComponent} from '../../widgets/dcm-drop-down/dcm-drop-down.component';
import {ModifiedWidgetComponent} from '../../widgets/modified-widget/modified-widget.component';
import {IssuerSelectorComponent} from '../../widgets/issuer-selector/issuer-selector.component';
import {CodeSelectorComponent} from '../../widgets/code-selector/code-selector.component';
import {RangePickerComponent} from '../../widgets/range-picker/range-picker.component';
import {MatRadioButton, MatRadioGroup} from '@angular/material/radio';
import {DropdownComponent} from '../../widgets/dropdown/dropdown.component';
import {OptionComponent} from '../../widgets/dropdown/option.component';
import {PersonNamePickerComponent} from '../../widgets/person-name-picker/person-name-picker.component';
import {ModalityComponent} from '../../widgets/modality/modality.component';
import {MatOption, MatSelect} from '@angular/material/select';
import {FormsModule} from '@angular/forms';
import {CommonModule, NgClass, NgStyle, NgSwitch} from '@angular/common';
import {SizeRangePickerComponent} from '../../widgets/size-range-picker/size-range-picker.component';

@Component({
    selector: 'filter-generator',
    templateUrl: './filter-generator.component.html',
    styleUrls: ['./filter-generator.component.scss'],
    imports: [
        MatProgressSpinner,
        TrimPipe,
        DcmDropDownComponent,
        ModifiedWidgetComponent,
        IssuerSelectorComponent,
        CodeSelectorComponent,
        RangePickerComponent,
        MatRadioGroup,
        MatRadioButton,
        DropdownComponent,
        OptionComponent,
        PersonNamePickerComponent,
        ModalityComponent,
        MatSelect,
        FormsModule,
        NgClass,
        NgStyle,
        SizeRangePickerComponent,
        MatOption,
        NgSwitch,
        CommonModule
    ],
    standalone: true
})
export class FilterGeneratorComponent implements OnInit, OnDestroy, AfterContentChecked {


    private _schema;
    @Input() model;
    private _filterTreeHeight = 2;
    @Input() filterID;
    @Input() hideClearButtons;
    @Input() filterIdTemplate;
    @Input() doNotSave;
    @Output() submit  = new EventEmitter();
    @Output() onChange  = new EventEmitter();
    @Output() onTemplateSet  = new EventEmitter();
    @Output() onFilterClear  = new EventEmitter();
    @Output() onFilterLoadFinish  = new EventEmitter();
    @Input() ignoreOnClear; // string[], pas here all filter keys that should be ignored on clear
    @Input() defaultSubmitId: string;
    // A function that will be triggered every time when the filter will change
    // (So one can manipulate the schema based on some value/dropdown/checkbox in the model)
    @Input() onFilterChangeHook: Function;
    dialogRef: MatDialogRef<any>;
    cssBlockClass = '';
    hideLoader = false;
    filterForm;
    parentId;
    filterTemplatePath = 'dcmDevice.dcmuiConfig["0"].dcmuiFilterTemplateObject';
    filterTemplates;
    showFilterTemplateList = false;
    showFilterButtons = false;
    hoverActive = false;
    noFilterFound = false;
    Array = Array;
    readonly dualIndex = [0, 1];
    dynamicAttributeConfig = {
        iods: [],
        dynamicAttributes: null,
        newAttribute: null,
        newValue: null,
        dropdownPlaceholder: $localize `:@@select_attribute:Select attribute`,
        labels: {
            dynamic_value: $localize `:@@dynamic_value:Dynamic value`,
            add_title: $localize `:@@add_title:Add`,
            delete_title: $localize `:@@delete_title:Remove`
        }
    }
    get schema() {
        return this._schema;
    }

    @Input()
    set schema(value) {
        this._schema = value;
        this.saveDataInMemory();
        this.triggerFilterLoadFinish();
    }
    constructor(
        private inj: Injector,
        private appService: AppService,
        private viewContainerRef: ViewContainerRef,
        public dialog: MatDialog,
        private deviceConfigurator: DeviceConfiguratorService,
        private devices: DevicesService,
        private rangePicker: RangePickerService,
        private studyService: StudyService
    ) {
        console.log('test', this._filterTreeHeight)
        this.getDataFromMemory();
    }

    triggerFilterLoadFinish() {
        if (this._schema && this._schema.length > 0 && this.filterTreeHeight) {
            this.onFilterLoadFinish.emit();
        }
    }

    getDataFromMemory() {
        try {
            if ((!j4care.isSet(this._schema) || this._schema.length === 0) && this.filterID) {
                const savedSchema = localStorage.getItem('schema_' + this.filterID);
                this._schema = JSON.parse(savedSchema);
            }
            if (!j4care.isSet(this._filterTreeHeight) && this.filterID) {
                const savedTreeHeight = localStorage.getItem('tree_height_' + this.filterID);
                this._schema = JSON.parse(savedTreeHeight);
            }
            if (j4care.isSet(this._schema) && this._filterTreeHeight) {
                this.hideLoader = true;
            }
        } catch (e) {}
    }

    saveDataInMemory() {
        try {
            if (j4care.isSet(this._schema) && this.filterID) {
                localStorage.setItem('schema_' + this.filterID, JSON.stringify(this._schema));
            }
            if (j4care.isSet(this._filterTreeHeight) && this.filterID) {
                localStorage.setItem('tree_height_' + this.filterID, JSON.stringify(this._filterTreeHeight));
            }
        } catch (e) {}
    }
    get filterTreeHeight() {
        return this._filterTreeHeight;
    }

    @Input('filterTreeHeight')
    set filterTreeHeight(value) {
        this._filterTreeHeight = value || 2;
        if (this._filterTreeHeight) {
            this.cssBlockClass = `height_${this._filterTreeHeight}`;
        }
        this.saveDataInMemory();
        this.triggerFilterLoadFinish();
    }

    ngOnInit() {
        this.getDataFromMemory();
        if (this._filterTreeHeight) {
            this.cssBlockClass = `height_${this._filterTreeHeight}`;
        }
        if (this.filterTreeHeight) {
            this.cssBlockClass = `height_${this.filterTreeHeight}`;
        }
        if (!this.filterID) {
            try {
                this.filterID = `${location.hostname}-${this.inj['view'].parentNodeDef.renderParent.element.name}`;
            } catch (e) {
                this.filterID = `${location.hostname}-${location.pathname.replace(/\/dcm4chee-arc\/ui2\//g, '').replace(/\//g, '-')}`;
            }
        }
        if (!_.isBoolean(this.doNotSave)) {
           let savedFilters = localStorage.getItem(this.filterID);
            let parsedFilter = JSON.parse(savedFilters);
            if (this.doNotSave) {
                this.doNotSave.forEach(f => {
                    if (parsedFilter && parsedFilter[f]) {
                        delete parsedFilter[f];
                    }
                })
            }
           if (savedFilters) {
               this.model = _.mergeWith(this.model, parsedFilter, (a, b) => {
                   if (a) {
                       return a;
                   }
                   if (!a && a != '' && b) {
                       return b;
                   } else {
                       return a;
                   }
               });
               this.onTemplateSet.emit(this.model);
           }
        }
        if (this._schema) {
/*            const test = _.flattenDepth(this.schema,this._filterTreeHeight).forEach(element=>{
                console.log("element",element);
            });*/
            j4care.penetrateArrayToObject(this._schema, (obj) => {
                if (obj.hasOwnProperty('tag') && obj['tag'] === 'dynamic-attributes' && obj.hasOwnProperty('iodFileNames')) {
                    console.log('iodFilenames', obj['iodFileNames']);
                    const iodFileNames = obj['iodFileNames'] || [
                        'patient',
                        'study'
                    ];
                    this.studyService.getIodObjectsFromNames(iodFileNames).subscribe(iod => {
                        this.dynamicAttributeConfig.iods = this.studyService
                                                                .iodToSelectedDropdown(iod.reduce((n0, n1) => Object.assign(n0, n1)));
                    });
                }
            });
        }
        this.onTemplateSet.emit(this.model);
    }

    getLabelFromIODTag(dicomTagPath) {
        return this.studyService.getLabelFromIODTag(dicomTagPath);
    }
    onKeyUp(e) {
        // console.log("e",e.code);
        if (e.keyCode === 13) {
            this.submitEmit(this.defaultSubmitId);
        }
    }

    addNewDynamicAttribute() {
        this.dynamicAttributeConfig.dynamicAttributes = this.dynamicAttributeConfig.dynamicAttributes || new Map();
        if (this.dynamicAttributeConfig.newAttribute && this.dynamicAttributeConfig.newValue) {
            this.dynamicAttributeConfig.dynamicAttributes.set(this.dynamicAttributeConfig.newAttribute, this.dynamicAttributeConfig.newValue);
            this.model[this.dynamicAttributeConfig.newAttribute] = this.dynamicAttributeConfig.newValue;
            this.dynamicAttributeConfig.newAttribute = undefined;
            this.dynamicAttributeConfig.newValue = '';
        }
        console.log('model', this.model);
    }

    removeDynamicAttribute(attr) {
        try {
            this.dynamicAttributeConfig.dynamicAttributes.delete(attr);
            delete this.model[attr];
        } catch (e) {

        }
        console.log('model', this.model);
    }
    submitEmit(id) {
        this.model = j4care.clearEmptyObject(this.model);
      if (id) {
        this.submit.emit({model: this.model, id: id});
      } else {
        this.submit.emit(this.model);
      }
    }
    filterChange(test) {
        console.log('this.model', this.model);
        console.log('this.schema', this._schema);
        if (this.onFilterChangeHook) {
            this.onFilterChangeHook(event, this.model, this._schema);
        }
        this.onChange.emit(this.model);

    }
    codeChanged(codes, e) {
        Object.keys(codes).forEach(code => {
            if (_.hasIn(e, codes[code].key)) {
                this.model[codes[code].key] = e[codes[code].key];
            } else {
                delete this.model[codes[code].key];
            }
        });
        this.filterChange(e);
    }
    issuerChanged(issuers, e) {
        Object.keys(issuers).forEach(issuer => {
            if (_.hasIn(e, issuers[issuer].key)) {
                this.model[issuers[issuer].key] = e[issuers[issuer].key];
            } else {
                delete this.model[issuers[issuer].key];
            }
        });
        this.filterChange(e);
    }
    modifiedWidget(e) {
        [
            'modified',
            'allmodified'
        ].forEach(key => {
            if (_.hasIn(e, key)) {
                this.model[key] = e[key];
            } else {
                delete this.model[key];
            }
        })
    }
    clear() {
        Object.keys(this.model).forEach(filter => {
           this.model[filter] = '';
        });
        this.onFilterClear.emit(this.model);
    }
    trackByFn(index, item) {
        return index; // or item.id
    }
    ngAfterContentChecked() {
        if (!this.hideLoader) {
            setTimeout(() => {
                this.hideLoader = true;
            }, 100);
        }
    }
    dateChanged(key, e) {
        if (e) {
            this.model[key] = e;
        } else {
            delete this.model[key];
        }
        this.filterChange(e);
    }
    splitDateRangeChanged(e) {
        if (e) {
            this.model['SplitStudyDateRange'] = e;
        } else {
            delete this.model['SplitStudyDateRange'];
        }
        this.filterChange(e);
    }
    confirm(confirmparameters) {
        // this.config.viewContainerRef = this.viewContainerRef;
        this.dialogRef = this.dialog.open(ConfirmComponent, {
            height: 'auto',
            width: '465px'
        });
        this.dialogRef.componentInstance.parameters = confirmparameters;
        return this.dialogRef.afterClosed();
    };
    createNewFilterTemplateToDevice(newTemplateName, device) {
        let newObject = {
            dcmuiFilterTemplateDefault: false,
            dcmuiFilterTemplateDescription: $localize `:@@filter-generator.test_description:Test description`,
            dcmuiFilterTemplateFilters: Object.keys(this.model).filter(m => {
                return this.model[m];
            }).map(k => {
                return `${k}=${this.model[k]}`
            }),
            dcmuiFilterTemplateGroupName: newTemplateName,
            dcmuiFilterTemplateID: this.filterIdTemplate || this.filterID
        };
        if (_.hasIn(device, this.filterTemplatePath)) {
            (<any[]>_.get(device, this.filterTemplatePath)).push(newObject);
        } else {
            _.set(device, this.filterTemplatePath, [newObject]);
        }
        console.log('device', device);
        return device;
    }

    removeFilterTemplate(filter) {
        this.confirm({
            content: $localize `:@@remove_filter_template:Are you sure you want to remove this filter-template?`
        }).subscribe((ok) => {
            if (ok) {
                console.log('filter', filter);
            }
        });
    }
    saveFilterTemplate() {
        if (!this.appService.deviceName) {
            this.confirm({
                content: $localize `:@@archive_device_name_not_found:Archive device name not found, reload the page and try again!`
            }).subscribe((ok) => {});
        } else {
            console.log('device name', this.appService.deviceName);
            this.deviceConfigurator.getDevice(this.appService.deviceName).subscribe(arch => {
                console.log('arch', arch);

                this.confirm({
                    content: $localize `:@@set_filter_template_name:Set the name for the new filter template:`,
                    input: {
                        name: 'newdevice',
                        type: 'text'
                    },
                    result: {input: ''},
                    saveButton: $localize `:@@SAVE:SAVE`
                }).subscribe((ok) => {
                    if (ok) {
                        console.log('result.input', ok.input);
                        let device = this.createNewFilterTemplateToDevice(ok.input, arch);
                        console.log('device', device);
                        this.devices.saveDeviceChanges(this.appService.deviceName, device).subscribe(res => {

                        }, err => {
                            console.error(err);
                        })
                    }
                });
            }, err => {
                console.log('arch', err);
            })
        }
    }
    openTemplateList() {
        if (!this.appService.deviceName) {
            this.confirm({
                content: $localize `:@@archive_device_name_not_found:Archive device name not found, reload the page and try again!`
            }).subscribe((ok) => {});
        } else {
            console.log('device name', this.appService.deviceName);
            this.showFilterTemplateList = true;
            this.deviceConfigurator.getDevice(this.appService.deviceName).subscribe(arch => {
                if (_.hasIn(arch, this.filterTemplatePath)) {
                    this.filterTemplates = (<any[]>_.get(arch, this.filterTemplatePath)).filter(filter => {
                        return filter.dcmuiFilterTemplateID === this.filterIdTemplate;
                    });
                } else {
                    console.log('no filter template found');
                }
            }, err => {
                console.error(err);
            });
        }
    }
    mouseEnterFilter() {
        this.hoverActive = true;
        this.showFilterButtons = true;
    }
    mouseLeaveFilter() {
        this.hoverActive = false;
        setTimeout(() => {
            if (this.hoverActive === false) {
                this.showFilterTemplateList = false;
                this.showFilterButtons = false;
            }
        }, 500);
    }
    inFilterClicked() {
        this.showFilterTemplateList = false;
    }
    openTemplateFilter(filter) {
        this.showFilterTemplateList = false;
        this.showFilterButtons = false;
        const regex = /(\w*)=(\w*)/;
        let newObject = {};
        let m;
        filter.dcmuiFilterTemplateFilters.forEach(filter => {
            if ((m = regex.exec(filter)) !== null) {
                    newObject[m[1]] = m[2];
            }
        });
        console.log('newOjbect', newObject);
        this.model = newObject;
        this.onTemplateSet.emit(this.model);

    }
    ngOnDestroy() {
        if (!_.isBoolean(this.doNotSave)) {
            if (this.doNotSave) {
                this.doNotSave.forEach(f => {
                    if (this.model[f]) {
                        delete this.model[f];
                    }
                })
            }
            localStorage.setItem(this.filterID, JSON.stringify(this.model));
            this.saveDataInMemory();
        }
    }
}
