### pgSqlBlocks

pgSqlBlocks - это standalone приложение, написанное на языке программирования Java, 
которое позволяет легко ориентироваться среди процессов и получать информацию о блокировках и ожидающих запросов в СУБД PostgreSQL. 
Отображается информация о состоянии подключения к БД, а также информация о процессах в БД.

Требуется Java JRE версии 1.8 и выше для вашей платформы.
Запуск jar-файла через консоль командой _java -jar pgSqlBlocks-1.3.6-Linux-64.jar_

* Для пользователей Gtk3, если возникают сложности с отображаемыми всплывающими сообщениями, рекомендуется запускать приложение с ключом *SWT_GTK3=0*.

### Структура пакетов

_ru.taximaxim.pgsqlblocks_: В данном пакете находятся все классы, реализующие бизнес логику проекта.

_ru.taximaxim.pgsqlblocks.ui_: Тут лежит все, что связано с UI.

_ru.taximaxim.pgsqlblocks.utils_: Тут находятся утилиты для работы с фильтрами процессов, иконками, директориями, настройками, XML и чтения файла pgpass.

### Бизнес логика

Есть 3 основных класса:

* `DbcData.java`
В данном классе хранится вся необходимая для соединения с БД информация + его статус (private DbcStatus status).
Каждому статусу необходимо указывать соответсвующую иконку (public String getImageAddr()). Иконки хранятся в src/main/resources/images.
Эти данные сохраняются в формате xml. Вся работа с xml реализована в DbcDataListBuilder.java.

* `Process.java`
Данный класс хранит информацию о процессах DB.
Данные реализованы в виде дерева. У процесса могут быть потомки(блокируемые данным процессом процессы) и родитель(блокирующий процесс).

* `DbcDataRunner.java`
В нем реализуется связь, отправка запросов и получение данных с БД. Все новые запросы необходимо оборачивать в Runnable и выполнять в отдельном потоке с помощью ScheduledExecutorService, к примеру, во избежании блокировки/подтормаживания ui-потока.
В начале необходимо уведомить, что происходит обновление информации о процессах в подключенной БД: dbcData.setInUpdateState(false)
Для уведомления ui-потока о том, что запрос отработал, необходимо в его конце вызвать dbcData.setInUpdateState(false) и dbcData.notifyUpdated();;

### Запросы

Для получения всех процессов сервера используется [скрипт](src/main/resources/query.sql).

Для получения всех процессов сервера, включая idle(бездействующие), используется [скрипт](src/main/resources/query_with_idle.sql).

Уничтожается процесс командой: _select pg_terminate_backend(?);_

Послать сигнал для отмены процесса: _select pg_cancel_backend(?);_

### UI
Все, что связано с UI необходимо писать в пакете ru.taximaxim.pgsqlblocks.ui
Основной UI приложения написан в классе MainForm.java.

### Homepage

http://pgcodekeeper.ru/pgsqlblocks.html

### License

This application is licensed under the Apache License, Version 2.0. See LICENCE for details.