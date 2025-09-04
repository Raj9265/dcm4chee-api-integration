import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';

import { PersonNamePickerComponent } from './person-name-picker.component';
import {MatDialogRef} from "@angular/material/dialog";
import {AppService} from "../../app.service";
class PatientNameDependenc{
}
describe('PatientNamePickerComponent', () => {
  let component: PersonNamePickerComponent;
  let fixture: ComponentFixture<PersonNamePickerComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [PersonNamePickerComponent],
        providers: [
          { provide: AppService, useClass: PatientNameDependenc }
        ],
      teardown: { destroyAfterEach: false }
  })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PersonNamePickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
