import struct,sys,json
d=open(sys.argv[1],'rb').read()
n=0x148//4
w=struct.unpack('<%dI'%n,d[:n*4])
print('SP',hex(w[0]),'Reset',hex(w[1]))
c=__import__('collections').Counter(w[2:])
dflt=[k for k,v in c.most_common(3)]
print('most common handlers',[ (hex(k),v) for k,v in c.most_common(4)])
names={2:'NMI',3:'HardFault',4:'MemManage',5:'BusFault',6:'UsageFault',11:'SVCall',12:'DebugMon',14:'PendSV',15:'SysTick'}
gd={0:'WWDGT',1:'LVD',2:'TAMPER',3:'RTC',4:'FMC',5:'RCU_CTC',6:'EXTI0',7:'EXTI1',8:'EXTI2',9:'EXTI3',10:'EXTI4',11:'DMA0_Ch0',12:'DMA0_Ch1',13:'DMA0_Ch2',14:'DMA0_Ch3',15:'DMA0_Ch4',16:'DMA0_Ch5',17:'DMA0_Ch6',18:'ADC0_1',19:'USBD_HP_CAN0_TX',20:'USBD_LP_CAN0_RX0',21:'CAN0_RX1',22:'CAN0_EWMC',23:'EXTI5_9',24:'TIMER0_BRK',25:'TIMER0_UP',26:'TIMER0_TRG_CMT',27:'TIMER0_Channel',28:'TIMER1',29:'TIMER2',30:'TIMER3',31:'I2C0_EV',32:'I2C0_ER',33:'I2C1_EV',34:'I2C1_ER',35:'SPI0',36:'SPI1',37:'USART0',38:'USART1',39:'USART2',40:'EXTI10_15',41:'RTC_Alarm',42:'USBD_WKUP',43:'TIMER7_BRK',44:'TIMER7_UP',45:'TIMER7_TRG_CMT',46:'TIMER7_Channel',47:'ADC2',48:'EXMC',49:'SDIO',50:'TIMER4',51:'SPI2',52:'UART3',53:'UART4',54:'TIMER5',55:'TIMER6',56:'DMA1_Ch0',57:'DMA1_Ch1',58:'DMA1_Ch2',59:'DMA1_Ch3_4',60:'DMA1_Ch4',61:'ENET',62:'ENET_WKUP',63:'CAN1_TX',64:'CAN1_RX0',65:'CAN1_RX1',66:'CAN1_EWMC',67:'USBFS'}
for i in range(1,n):
    h=w[i]
    if h==0: continue
    nm=names.get(i) or ('IRQ%d(%s?)'%(i-16,gd.get(i-16,'?')) if i>=16 else 'sys%d'%i)
    flag='' if h not in dflt[:1] else '  [common/default]'
    if h in dflt[:1] and i>=16: continue
    print(i,nm,hex(h),flag)
