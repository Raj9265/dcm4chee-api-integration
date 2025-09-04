import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { CodeSelectorComponent } from './code-selector.component';

describe('CodeSelectorComponent', () => {
  let component: CodeSelectorComponent;
  let fixture: ComponentFixture<CodeSelectorComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
    declarations: [CodeSelectorComponent],
    teardown: { destroyAfterEach: false }
})
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CodeSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
