declare module '@wangeditor/editor' {
  export interface IToolbarConfig {
    [key: string]: unknown
  }

  export interface IEditorConfig {
    [key: string]: unknown
  }

  export interface IDomEditor {
    getHtml?: () => string
    destroy?: () => void
    [key: string]: unknown
  }
}

declare module 'vue-advanced-cropper' {
  import type { DefineComponent } from 'vue'

  export const Cropper: DefineComponent<Record<string, unknown>, Record<string, unknown>, any>
}
