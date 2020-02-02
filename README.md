# dubbo-plug
[![996.icu](https://img.shields.io/badge/link-996.icu-red.svg)](https://996.icu)
[![LICENSE](https://img.shields.io/badge/license-Anti%20996-blue.svg)](https://github.com/996icu/996.ICU/blob/master/LICENSE)

dubbo 接口调试插件

功能：
 - 连接注册中心，获取服务信息 

    当前支持 redis, zookeeper, consul
    
    例如: redis://127.0.0.1:6379?timeout=30000
 
 - 服务接口调用，并发测试
 
方法参数填写：

    接口需要的参数使用yaml语法进行填充
    yaml 一个根对象代表接口需要的一个参数
    如果参数为复杂bean，按照yaml语法创建对象即可
    
例如：

基本类型
Object fun1(int a, String b);
~~~yaml
- 123
- paramb
~~~

Bean类型
Object fun1(Cat cat1, Cat cat2);
~~~java
class Cat{
    int age;
    String name;
}
~~~
~~~yaml
- age: 12
  name: tom
- age: 2
  name: jack
~~~
Map 参数同上

数组、Set、List等集合参数

Object fun1(int[] a);
~~~yaml
-
  - 12
  - 123
~~~

null 参数处理: 单独的 **-** 表示null