# Go 内存分配
> 本文主要对 [Go 语言内存分配器的实现原理](https://draveness.me/golang/docs/part3-runtime/ch07-memory/golang-memory-allocator/) 的总结；如有问题，欢迎批评指正！

## 常见分配器

### 线性分配器
![image](http://cdn.processon.com/60fbd91a1efad46a20a38186?e=1627121450&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:iU5C3MuxrEmQFR7nara6WH-cnkA=)
#### 定义
只需要在内存中维护一个指向内存特定位置的指针，当用户程序申请内存时，分配器只需要检查剩余的空闲内存、返回分配的内存区域并修改指针在内存中的位置，即移动下图中的指针。


#### 优点
有较快的执行速度，以及较低的实现复杂度；

#### 缺点
无法在内存被释放时重用内存，因此，需要合适的垃圾回收算法配合使用，标记压缩（Mark-Compact）、复制回收（Copying GC）和分代回收（Generational GC）等算法可以通过拷贝的方式整理存活对象的碎片，将空闲内存定期合并。


### 空闲链表分配器
![image](http://cdn.processon.com/60fbd9330791294ae09f54ca?e=1627121475&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:GKjbXzdd_ZTtOX5lLaT8yRGPkwY=)
#### 定义
只需要在内存中维护一个指向内存特定位置的指针，当用户程序申请内存时，分配器只需要检查剩余的空闲内存、返回分配的内存区域并修改指针在内存中的位置，即移动下图中的指针。


下面我们介绍几种常见的分配策略。

#### 首次适应
首次适应（First-Fit）：从链表头开始遍历，选择第一个大小大于申请内存的内存块；


#### 循环首次适应
循环首次适应（Next-Fit）：从上次遍历的结束位置开始遍历，选择第一个大小大于申请内存的内存块；
 


#### 首次适应
最优适应（Best-Fit）：从链表头遍历整个链表，选择最合适的内存块；


#### 隔离适应分配策略
![image](http://cdn.processon.com/60fbd94a0791294ae09f54e1?e=1627121498&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:1i3R_Wm-pyT12KjgPiE0jTwloyo=)
- 隔离适应（Segregated-Fit）：将内存分割成多个链表，每个链表中的内存块大小相同，申请内存时先找到满足条件的链表，再从链表中选择合适的内存块；

> 例如：通过该策略会将内存分割成由 4、8、16、32 字节的内存块组成的链表，当我们向内存分配器申请 8 字节的内存时，我们会在上图中的第二个链表找到空闲的内存块并返回。隔离适应的分配策略减少了需要遍历的内存块数量，提高了内存分配的效率。

- Go 语言使用的内存分配策略与隔离适应策略有些相似。

## 分级分配策略
- 采用类似 TCMalloc （Thread-Caching Malloc）的分配策略，`它的核心理念是使用多级缓存将对象根据大小分类，并按照类别实施不同的分配策略，提高内存分配器的性能。`
 

注：这种多层级的内存分配设计与计算机操作系统中的多级缓存也有些类似，因为多数的对象都是小对象，我们可以通过线程缓存和中心缓存提供足够的内存空间，发现资源不足时就从上一级组件中获取更多的内存资源。


### 多级缓存
![image](http://cdn.processon.com/60fbd8375653bb3ddc10deb2?e=1627121223&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:fu9dLZ9ZpJd-UC7jOA360GGSlzw=)
- Go 运行时分配器都会引入Thread Cache(mcache)、Central Cache(mcentral)和Page Heap(mheap)三个组件分级管理内存：

- 线程缓存属于每一个独立的线程，它能够满足线程上绝大多数的内存分配需求，因为不涉及多线程，所以也不需要使用互斥锁来保护内存，这能够减少锁竞争带来的性能损耗。当线程缓存不能满足需求时，运行时会使用中心缓存作为补充解决小对象的内存分配，在遇到 32KB 以上的对象时，内存分配器会选择页堆直接分配大内存。

### 对象分类
![image](http://cdn.processon.com/60fbd62e7d9c083494e4f19f?e=1627120702&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:mxrfNifUne077BbrRHZWe1wHZxA=)
内存分配器会根据申请分配的内存大小选择不同的处理逻辑，运行时根据对象的大小将对象分成微对象、小对象和大对象三种；

### 微对象 (0, 16B)
##### 定义
将小于 16 字节的非指针类型对象划分为微对象。
#### 分配步骤
使用 mcache 上的 tiny 分配器提高分配的性能，依次尝试从mcache、mcentral、mheap 分配内存；


### 小对象 [16B, 32B]
#### 定义
指大小为 16 字节到 32,768 字节的对象以及所有小于 16 字节的指针类型的对象。
#### 分配步骤
小对象的分配可以被分成以下的三个步骤：
1. 确定分配对象的大小以及跨度类 runtime.spanClass；
2. 从 mcache、mcentral、mheap 中获取内存管理单元并从内存管理单元找到空闲的 mspan；
调用；
3. runtime.memclrNoHeapPointers 清空空闲内存中的所有数据；


### 大对象 (32B, +∞)
#### 定义
指对于大于 32KB 的对象。
#### 分配步骤
不会从 mcache 或者 mcentral 中获取mspan，而是直接调用 runtime.mcache.allocLarge() 计算分配该对象所需要的页数，它按照 8KB 的倍数在堆上申请内存：
注：申请内存时会创建一个跨度类为 0 的 spanClass 并调用 runtime.mheap.alloc() 分配一个管理对应内存的管理单元。




## 堆区虚拟内存布局的演化

### 线性内存（&lt; 1.11）
![image](http://cdn.processon.com/60f1837d637689739c3a28dd?e=1626444173&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:NsYeZoiB9D61Yp8716qQVqDVPR0=)


#### 设计思想 
- 建立在堆区的内存是连续的这一假设上。
- 在垃圾回收时会根据指针的地址判断对象是否在堆中，并通过上一段中介绍的过程找到管理该对象的 mspan。 


#### 内部实现
Go 语言程序的 1.10 版本在启动时会初始化整片虚拟内存区域，如下所示的三个区域 spans、bitmap 和 arena 分别预留了 512MB、16GB 以及 512GB 的内存空间，这些内存并不是真正存在的物理内存，而是虚拟内存。

- arena 区：大小为512G,由一个个 page 组成,每个 page 8KB,一共有512GB/8KB 个页

- spans 区：存放指向 arean 中 page 
 所属的 span 的指针,一个指针为 8byte 所以 span 区域的大小为【(512GB/一个page8KB)*一个指针8byte= 512M】

- bitmap 主要用于GC, 用两个 bit 表示 arena 中一个字的可用状态,一个字为8 byte，所以 bitmap 区域的大小为【(512G/一个字 8byte)*2bit/每个字节8个bit=16G】




#### 优缺点 
设计虽然简单并且方便，但是在 C 和 Go 混合使用时会导致程序崩溃。

- 分配的内存地址会发生冲突，导致堆的初始化和扩容失败；
- 没有被预留的大块内存可能会被分配给 C 语言的二进制，导致扩容后的堆不连续； 
导致如果不预留内存空间却会在特殊场景下造成程序崩溃。而预留大块内存空间而不使用是不切实际的；
- 内存不可以超过512G；


### 二维稀疏内存（&gt;= 1.11 ）
![image](http://cdn.processon.com/60f183965653bb0b4888744a?e=1626444198&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:-ezqq2siR9S3xj2wh-4kmh3ckcw=)

#### 设计思想
将原有的连续大内存切分成稀疏的小内存，而用于管理这些内存的元信息也被切分成了小块。

#### 内部实现

- 改成了两维稀疏索引的方式. 内存可以超过512G, 也可以允许不连续的内存.

- mheap 中的 areans 字段是一个指针数组, 每个 heapArena 管理 64M 的内存.

- bitmap 和 spans 和上面的功能一致.




#### 优缺点
不仅能移除堆大小的上限,还能解决 C 和 Go 混合使用时的地址空间冲突问题，但也使内存管理变得更加复杂，对垃圾回收稍有影响，大约会增加 1% 的垃圾回收开销。

## 内存管理组件
![image](http://cdn.processon.com/60f180f0f346fb3f3413633c?e=1626443520&amp;token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:J_cKMnefNHwnZ7NVO0doJ2UZq-4=)


### mspan(内存管理的基本单元)
![image](http://cdn.processon.com/60fa7769e401fd7e99796e30?e=1627030906&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:SoPfKOusCWBAJ_oGalzVTBFdtow=)
- mspan之间形成双向链表结构，每个mspan 都管理 n 个大小为 8KB 的页


- 以页为单位向堆申请内存，以对象为单位查找分配空间

- 每个 mspan 都有一个 spanClass 属性




#### mspan 结构体
![image](http://cdn.processon.com/60f66a3b1efad43a71d8f235?e=1626765387&amp;token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:3Ji0lwXPDksbObPzQo1KGxnP1JY=)

上图表示 slot 大小为 32byte 的 span, 上一次gc之后, 前8个slot使用如上。

- freeindex表示 < 该位置的都被分配了, >= 该位置的可能被分配, 也可能没有. 配合allocCache来寻找. 每次分配后, freeindex设置为分配的slot+1。

-  allocBits表示上一次GC之后哪一些slot被使用了: 0未使用或释放, 1已分配。

- allocCache 表示从 freeindex 开始的64个 slot 的分配情况, 1为未分配, 0为分配. 使用 ctz(Count Trailing Zeros指令)来找到第一个非0位. 使用完了就从 allocBits 加载, 取反。

- 每次gc完之后, sweep阶段, 将allocBits 设置为 gcmarkBits。


#### 134 种 spanClass 
![image](http://cdn.processon.com/60f66a481e08534af6c9aa9d?e=1626765400&amp;token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:6lNhEL0_K5UFnIyvniD632IH_pg=)
`有67种 size class，在用 noscan 区分的话，则一共有134种 spanClass。`

- spanClass 表示一个 span 的 size Class 和 是否含有 noscan 标记位。

- size class 决定了 mspan 存储的对象大小和个数；每种 size class 都会存储特定大小的对象、包含特定数量的页数以及对象，其中 class=0 的特殊跨度类，它能够管理大于 32KB 的特殊对象；

- noscan 标记位：表示存储对象是否包含指针，方便后期垃圾回收；每个 size class 都有一个 noscan spanClass 和一个 scan spanClass。 noscan spanClass 只包含 noscan 对象，它不包含指针，因此不需要被垃圾收集器扫描。



上面图表展示了对象大小从 8B 到 32KB，总共 66 种跨度类的大小、存储的对象数以及浪费的内存空间，以表中的第四个跨度类为例，跨度类为 4 的 mpsan 中对象的大小上限为 48 字节、管理 1 个页、最多可以存储 170 个对象。因为内存需要按照页进行管理，所以在尾部会浪费 32 字节的内存，当页中存储的对象都是 33 字节时，最多会浪费 31.52% 的资源：
(48−33)∗170+328192=0.31518


### mcache(线程缓存)
![image](http://cdn.processon.com/60fa77471e085366ea4bd262?e=1627030871&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:i8ebxuIppLzClrka1pdW1poq9As=)
- 每一个 mcache 都持有 67 * 2 个 mspan；
- 同处理器P绑定，主要用来缓存用户程序申请的微小对象。无锁访问内存管理单元。

注：上图中第二行 mspan 标号错了，应该是66

#### 初始化
初始化时是不包含 mspan (所有mspan都是空的占位符 emptymspan)，只有当用户程序申请内存时才会从上一级组件获取新的 mspan 满足内存分配的需求。


#### 替换
如何向 mcache 中插入 mspan?
如果 mcache 中没有找到可用的 mspan 将使用 mcentral 中的可用 mspan 将其替换。



#### tiny 分配器


##### &nbsp;由三个字段组成
- mcache 中包含三个用于分配微对象的字段。这三个字段组成了tiny 分配器（微对象分配器）。


```
type mcache struct {
	tiny             uintptr
	tinyoffset       uintptr
	local_tinyallocs uintptr
}
```


##### &nbsp;默认管理16 字节以下非指针类型对象内存
- 只管理非指针类型对象的内存；

- 管理的内存块大小 maxTinySize 是可以调整的，默认为 16 字节，maxTinySize 的值越大，组合多个对象的可能性就越高，内存浪费也就越严重；maxTinySize 越小，内存浪费就会越少，不过无论如何调整，8 的倍数都是一个很好的选择。


##### 具体分配逻辑
![image](http://cdn.processon.com/60fbdfd6637689719d24bcb6?e=1627123174&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:7tYqF19251kA7rvypUqbqiKnXXU=)

如上图所示，tiny 分配器已经在 16 字节的内存块中分配了 12 字节的对象，如果下一个待分配的对象小于 4 字节，它就会直接使用上述内存块的剩余部分，减少内存碎片，不过该内存块只有在 3 个对象都被标记为垃圾时才会被回收。


### mcentral(中心缓存)
![image](http://cdn.processon.com/60fa7971f346fb1b4f5dd739?e=1627031425&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:ULdj-ED3A7RPpnSNwYzyk2L1Q4E=)
- 每个 mcentral 都是一种mspan的全局后备资源，因此共有 134 个 mcentral；
- 访问 mcentral 中的 mspan 需要使用互斥锁；
 

#### 内含2个 mspanList
为了方便后续回收和查找，mcentral 内含 2个 mspanList：
- empty mspanList：有空闲对象的未满 mspan 列表；
- nonempty mspanList：没有空闲对象的满的 mspan 列表；

注：该结构体在初始化时，两个链表都不包含任何内存

#### mcache 从 mcentral 获取 mspan 过程
1.从有 empty mspanList 中查找可以使用的 mspan；
2.从 nonempty mspanList 中查找可以使用的 mspan；
3.调用 runtime.mcentral.grow 从堆中申请新的 mpsan；
注：无论通过哪种方法获取到了 mspan，最后都会对 mspan 中 allocCache 等字段进行更新，让运行时在分配内存时能够快速找到空闲的对象。

### mheap(页堆)
一个全局的结构体，统一管理堆上初始化的所有对象

#### 持有的 134 个mcentral
![image](http://cdn.processon.com/60fa7d477d9c083494e3c382?e=1627032407&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:1JOSGDoJmWrU9YO_U262VzZMWFY=)


#### 内部采用二维矩阵 runtime.heapArena 管理所有内存空间，内存可以是不连续的
![image](http://cdn.processon.com/60fa7d527d9c083494e3c3b2?e=1627032418&token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:Pj9MAjpr5zcVSY-pS2jvUHJKV9Y=)


#### 不同平台和架构具体参数
![image](http://cdn.processon.com/60fbbedc7d9c083494e4d19d?e=1627114733&amp;token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:waVLJ3AGS6EID5szysWTjQL9buU=)
表格中展示了不同平台上 Go 语言程序管理的堆区大小以及 heapArea 占用的内存空间；

- Linux 的 x86-64架构上,单个 Go 语言程序的内存上限也就是 256TB (256TB = 4M * 64MB)；
注释：二维数组的一维大小会是 1，而二维大小是 4M(4,194,304)，因为每一个指针占用 8 字节的内存空间，所以元信息的总大小为 32MB；由于每个 runtime.heapArena 都会管理 64MB 的内存，整个堆区最多可以管理 256TB 的内存。


### 之间联系

#### mcentral 属于 mheap，mhepa 会从操作系统中申请内存，会维护全局的 mspan，各个线程会通过 mcentral 获取新的 mspan

## 栈缓存&nbsp;stackcache
![image](http://cdn.processon.com/60fbe584f346fb1b4f5f3267?e=1627124628&amp;token=trhI0BY8QfVrIGn9nENop6JAc6l5nZuxhjQ62UfM:eGXgVkKeWUsRESVioAXFR-4iLwU=)
mcache 中 stackcache 用于分配 groutine 的stack 内存，和普通对象内存一样栈分配也有多级和多个层次；


分配步骤：
- <16K 先从 mcache 的 stackcache 中分配，如果无法分配，则需要从全局stackpool 分配出一批 stack, 赋给该 mcache 的 stackcache, 再从 stackcache 中分配；
- >16K 的直接从全局的 stackLarge 分配
注：上图是 linux 系统中的 stackcache 的示意图，stackfreelist 保存着空闲的stacks列表，分别缓存 2KB、4KB、8KB 和 16KB 的栈内存。 




## 参考资料

### [Draveness-内存分配器](https://draveness.me/golang/docs/part3-runtime/ch07-memory/golang-memory-allocator/)

### [golden-内存分配PPT](https://gitee.com/zhaojinxin_golden/article-images/blob/master/go/golden-%E5%86%85%E5%AD%98%E5%88%86%E9%85%8D.pptx)
