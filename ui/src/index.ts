import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiHeartPulseLine from '~icons/ri/heart-pulse-line'
import '@/assets/index.css'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/homeward',
        name: 'HomewardRootConsole',
        component: () => import('./views/console/ConsoleDashboard.vue'),
        meta: {
          title: '归航寻亲',
          searchable: true,
          permissions: ['plugin:homeward:console:view'],
          menu: {
            name: '归航寻亲',
            group: 'content',
            icon: markRaw(RiHeartPulseLine),
            priority: 40,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
