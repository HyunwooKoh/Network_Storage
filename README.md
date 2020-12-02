# Network_Storage
## FOR PEOPEL WHO SEARCHING 'SEARCHING LOCAL STORAGE AND MOUNT IT "
At NetworkStoragemanager.mount() this app try to mount on your device! but, you have to change it which your own API or
use jcifs-ng library. Also, This app is made for run on Android TV, so chagne the UI class if you run on different device. thanks

## 발생했던 문제점

1. Network 단절
2. Vpn 설정 변경
3. Samba Host Down
위와 같은 이벤트 발생시, 기기가 전반적으로 느려짐.

## 해결 방법

NetworkStatusListener Service class를 통하여 위의 세가지 상태 변경시, 각 상황에 따른 이벤트를 처리함.
각 Storage Info의 DB에는 Ismounted 라는 가장 최근에 사용자의 의도에 따른 mount / unmount 상태를 저장하는 column 존재.
해당 column의 값을 기준으로 Network 단절 -> Network 연결시 자동으로 Mount 진행

# 1. Network 단절
ConnectivityManager.NetworkCallback을 통해, connect / disconnect 이벤트를 받으며, disconnect시에는 모든 NetworkStorage unmount 진행
이때, 각 Info의 Ismounted 값은 변경하지 않고, connect시 이 값을 보고, 해당 storage들을 다시 mount 시킴

# 2. VPN 설정 변경
-- VPN off -> on의 경우 NetworkCallback의 OnAvailable() 및 OnLost()를 callback 받지 않지만,
-- VPN on -> off의 경우 callback을 받을 때도 있고, 받지 못할 때도 있음. *

따라서 두 경우 모두, broadcast receiver를 통해 android.net.conn.CONNECTIVITY_CHANGE 인텐트를 받으며, 이때 VPN Status를 확인.
변경 이전의 VPN Status를 Latest_vpn_stat 변수로 저장해 놓은 후, 변경된 것을 확인하면, 강제적으로 모든 network storage에 대해 unmount / mount 시킴.
이때, callback을 받아도, unmount / mount를 진행하지 않도록 하기위해, ON_VPN_STATUS 플래그를 사용.
VPN Status change -> ON_VPN_STATUS = true -> unmount() -> mount() -> ON_VPN_STATUS = false 순으로 진행 됨.

# 3. Samba Host Down
jcifs 라이브러리의 watch 함수를 사용. 이때, 하나의 SmbFile(Network Storage)에 대하여 1개의 thread가 돔.
Watcher_thread 및 Watching_SMB_List라는 두개의 ArrayList로 이 thread들을 관리. ->
MountBroadcast / UnmountBroadcast 를 보낼 때, 각각의 broadcast에서 추가적으로 하나씩 더 날림으로
service에서 Mount 되었을 경우, 해당 List들에 자료를 추가 및 새로운 감시 thread를 돌림.
Unmount 되었을 경우, 해당 List에서 Info의 name 값을 통해 thread를 찾고, 해당 thread를 interrupt 시킴으로써 감시 중단 및 thread 회수.
또한 이 watcher가 앞선 두 개의 Network 단절 및 VPN 변경 등의 이벤트도 interrupt로 인식해 이를 잡는데,
위의 두 경우와 Host가 Down되는 것은 다음과 같은 차이가 있음.

-- 위의 두 경우 --
VPN설정 변경시 단순히 Mount 되어있던 Storage들을 unmount 후 mount 시킴으로 오류를 해결할 수 있음 -> Storage Info의 IsMounted값을 사용
Network 단절 -> Network 연결 시에는 Storage Info의 IsMounted값을 변경하지 않고, 이를 사용하여 다시 연결되었을 때 자동으로 해당 Storage들을 mount 시킬 수 있음.

-- Host Down --
이는 사용자가 Host의 상태를 확인하여 다시 samba를 가동하거나 설정을 변경한 후 이용 가능한 상황으로 판단하였으므로, 해당 경우에 대해선
일반적인 unmount(사용자가 직접 하는 경우)와 같이 IsMounted의 값을 변경하도록 하였으므로, 위의 두 경우와 같은 API로는 제어할 수 없음. *

따라서, 해당 watcher에서 오류가 잡혔을 경우, 강제적으로 1초간 sleep하도록 한 후, 다른 두 경우에서 사용하는 unmount() 함수에서
ON_UNMOUNTING = Watching_SMB_List.size(); 와 같이 thread들을 control하기 위한 변수를 설정해 두고, 해당 thread들은 ON_UNMOUNTING == 0인 경우에만
unmount를 진행하도록 하고, 0이 아니면 ON_UNMOUNTING-- 연산만을 진행하도록 함.
이를 통해 위의 두 경우에서 unmount를 두번하도록 하지 않게 하고, IsMounted의 값을 유지할 수 있도록 하였다.
