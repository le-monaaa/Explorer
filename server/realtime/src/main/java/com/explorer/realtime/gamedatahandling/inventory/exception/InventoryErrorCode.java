package com.explorer.realtime.gamedatahandling.inventory.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum InventoryErrorCode {

    EMPTY_INVENTORY("인벤토리가 비어있습니다.", HttpStatus.BAD_REQUEST),
    SAME_INDEX("아이템을 이동하려는 인덱스가 같습니다.", HttpStatus.BAD_REQUEST),
    OUT_OF_RANGE_INDEX("인덱스가 인벤토리 최대 인덱스의 범위를 벗어났습니다.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus httpStatus;

}
